#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
车位预测系统
预测未来2小时哪些车位会空出来
高峰期提前推送提醒
"""

import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
import logging
import json
import os
from collections import defaultdict

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class SpotPrediction:
    spot_number: str
    plate_number: str
    parking_lot_id: int
    occupied_since: datetime
    predicted_exit_time: datetime
    confidence: float
    current_duration_minutes: int
    minutes_until_available: int
    zone: str = ""
    status: str = "OCCUPIED"


@dataclass
class PeakHourPrediction:
    is_peak_hour: bool
    will_be_peak_soon: bool
    peak_start_time: Optional[datetime]
    peak_end_time: Optional[datetime]
    expected_occupancy_rate: float
    available_spots_estimate: int


class HistoricalPatternAnalyzer:
    """历史停车模式分析器"""
    
    def __init__(self):
        self.historical_data = defaultdict(list)
        self.daily_patterns = defaultdict(lambda: defaultdict(list))
        self.vehicle_patterns = defaultdict(list)
        
    def add_parking_record(self, record: Dict):
        plate_number = record.get('plate_number', '')
        entry_time = record.get('entry_time')
        exit_time = record.get('exit_time')
        duration = record.get('duration_minutes', 0)
        
        if entry_time:
            if isinstance(entry_time, str):
                entry_time = datetime.fromisoformat(entry_time.replace('Z', '+00:00'))
            
            day_of_week = entry_time.weekday()
            hour = entry_time.hour
            
            if duration > 0:
                self.daily_patterns[day_of_week][hour].append(duration)
        
        if plate_number and duration > 0:
            self.vehicle_patterns[plate_number].append(duration)
        
        self.historical_data[plate_number].append({
            'entry_time': entry_time,
            'exit_time': exit_time,
            'duration': duration
        })
    
    def get_average_duration(self, plate_number: str = None) -> float:
        if plate_number and plate_number in self.vehicle_patterns:
            durations = self.vehicle_patterns[plate_number]
            if durations:
                return np.mean(durations)
        
        all_durations = []
        for day in self.daily_patterns.values():
            for hour in day.values():
                all_durations.extend(hour)
        
        return np.mean(all_durations) if all_durations else 120.0
    
    def get_time_based_prediction(self, current_time: datetime) -> float:
        day_of_week = current_time.weekday()
        hour = current_time.hour
        
        durations = self.daily_patterns[day_of_week].get(hour, [])
        
        if not durations:
            for nearby_hour in [hour-1, hour+1, hour-2, hour+2]:
                if 0 <= nearby_hour <= 23:
                    nearby_durations = self.daily_patterns[day_of_week].get(nearby_hour, [])
                    if nearby_durations:
                        return np.mean(nearby_durations)
            
            return 120.0
        
        return np.mean(durations)
    
    def get_vehicle_history(self, plate_number: str) -> List[Dict]:
        return self.historical_data.get(plate_number, [])
    
    def calculate_confidence(self, plate_number: str, current_duration: int, 
                              predicted_duration: int) -> float:
        confidence = 0.6
        
        if plate_number in self.vehicle_patterns:
            history = self.vehicle_patterns[plate_number]
            if len(history) >= 3:
                variance = np.var(history)
                consistency = 1.0 / (1.0 + variance / 60.0)
                confidence += consistency * 0.2
        
        diff = abs(predicted_duration - current_duration)
        if diff < 30:
            confidence += 0.1
        elif diff > 120:
            confidence -= 0.1
        
        return min(0.99, max(0.5, confidence))


class PeakHourDetector:
    """高峰期检测器"""
    
    def __init__(self):
        self.peak_hours = {
            'weekday_morning': {'start': 7, 'end': 9},
            'weekday_evening': {'start': 17, 'end': 19},
            'weekend': {'start': 10, 'end': 20}
        }
        
        self.historical_occupancy = defaultdict(lambda: defaultdict(list))
    
    def is_peak_hour(self, current_time: datetime) -> bool:
        day_of_week = current_time.weekday()
        hour = current_time.hour
        
        is_weekday = day_of_week < 5
        
        if is_weekday:
            morning_peak = self.peak_hours['weekday_morning']
            evening_peak = self.peak_hours['weekday_evening']
            
            return (morning_peak['start'] <= hour < morning_peak['end']) or \
                   (evening_peak['start'] <= hour < evening_peak['end'])
        else:
            weekend_peak = self.peak_hours['weekend']
            return weekend_peak['start'] <= hour < weekend_peak['end']
    
    def will_be_peak_soon(self, current_time: datetime, minutes_ahead: int = 30) -> bool:
        future_time = current_time + timedelta(minutes=minutes_ahead)
        return self.is_peak_hour(future_time) and not self.is_peak_hour(current_time)
    
    def get_next_peak_time(self, current_time: datetime) -> Optional[Tuple[datetime, datetime]]:
        day_of_week = current_time.weekday()
        hour = current_time.hour
        minute = current_time.minute
        
        is_weekday = day_of_week < 5
        
        if is_weekday:
            morning_start = datetime(current_time.year, current_time.month, current_time.day, 7, 0)
            morning_end = datetime(current_time.year, current_time.month, current_time.day, 9, 0)
            
            evening_start = datetime(current_time.year, current_time.month, current_time.day, 17, 0)
            evening_end = datetime(current_time.year, current_time.month, current_time.day, 19, 0)
            
            if current_time < morning_start:
                return (morning_start, morning_end)
            elif current_time < evening_start:
                return (evening_start, evening_end)
            else:
                next_day = current_time + timedelta(days=1)
                while next_day.weekday() >= 5:
                    next_day += timedelta(days=1)
                
                next_morning_start = datetime(next_day.year, next_day.month, next_day.day, 7, 0)
                next_morning_end = datetime(next_day.year, next_day.month, next_day.day, 9, 0)
                return (next_morning_start, next_morning_end)
        else:
            weekend_start = datetime(current_time.year, current_time.month, current_time.day, 10, 0)
            weekend_end = datetime(current_time.year, current_time.month, current_time.day, 20, 0)
            
            if current_time < weekend_start:
                return (weekend_start, weekend_end)
            else:
                next_monday = current_time + timedelta(days=(7 - day_of_week))
                next_morning_start = datetime(next_monday.year, next_monday.month, next_monday.day, 7, 0)
                next_morning_end = datetime(next_monday.year, next_monday.month, next_monday.day, 9, 0)
                return (next_morning_start, next_morning_end)
        
        return None
    
    def predict_occupancy_rate(self, current_time: datetime) -> float:
        if self.is_peak_hour(current_time):
            return 0.85
        elif self.will_be_peak_soon(current_time):
            return 0.70
        else:
            return 0.50


class SpotExitPredictor:
    """车位离场时间预测器"""
    
    def __init__(self):
        self.pattern_analyzer = HistoricalPatternAnalyzer()
        self.peak_detector = PeakHourDetector()
        
        self.time_of_day_factors = {
            'early_morning': (0, 6, 480),
            'morning_rush': (7, 9, 60),
            'midday': (10, 16, 120),
            'evening_rush': (17, 19, 45),
            'night': (20, 23, 180)
        }
    
    def predict_exit_time(self, spot_data: Dict) -> SpotPrediction:
        spot_number = spot_data.get('spot_number', '')
        plate_number = spot_data.get('plate_number', '')
        parking_lot_id = spot_data.get('parking_lot_id', 1)
        occupied_since = spot_data.get('occupied_since')
        zone = spot_data.get('zone', '')
        
        if isinstance(occupied_since, str):
            occupied_since = datetime.fromisoformat(occupied_since.replace('Z', '+00:00'))
        
        current_time = datetime.now()
        current_duration = int((current_time - occupied_since).total_seconds() / 60)
        
        predicted_duration = self._calculate_predicted_duration(
            plate_number, occupied_since, current_time
        )
        
        predicted_exit_time = occupied_since + timedelta(minutes=predicted_duration)
        
        if predicted_exit_time < current_time:
            predicted_exit_time = current_time + timedelta(minutes=30)
            predicted_duration = current_duration + 30
        
        confidence = self.pattern_analyzer.calculate_confidence(
            plate_number, current_duration, predicted_duration
        )
        
        minutes_until_available = int((predicted_exit_time - current_time).total_seconds() / 60)
        minutes_until_available = max(0, minutes_until_available)
        
        return SpotPrediction(
            spot_number=spot_number,
            plate_number=plate_number,
            parking_lot_id=parking_lot_id,
            occupied_since=occupied_since,
            predicted_exit_time=predicted_exit_time,
            confidence=confidence,
            current_duration_minutes=current_duration,
            minutes_until_available=minutes_until_available,
            zone=zone,
            status="OCCUPIED"
        )
    
    def _calculate_predicted_duration(self, plate_number: str, 
                                        occupied_since: datetime,
                                        current_time: datetime) -> int:
        predictions = []
        weights = []
        
        if plate_number:
            avg_duration = self.pattern_analyzer.get_average_duration(plate_number)
            history = self.pattern_analyzer.get_vehicle_history(plate_number)
            
            if len(history) >= 3:
                predictions.append(avg_duration)
                weights.append(0.4)
            elif len(history) >= 1:
                predictions.append(avg_duration)
                weights.append(0.2)
        
        time_based = self.pattern_analyzer.get_time_based_prediction(occupied_since)
        predictions.append(time_based)
        weights.append(0.3)
        
        tod_based = self._get_time_of_day_based_prediction(occupied_since)
        predictions.append(tod_based)
        weights.append(0.3)
        
        if self.peak_detector.is_peak_hour(occupied_since):
            predictions.append(60)
            weights.append(0.2)
        
        if self.peak_detector.is_peak_hour(current_time):
            current_time_based = self._get_time_of_day_based_prediction(current_time)
            predictions.append(current_time_based * 1.2)
            weights.append(0.1)
        
        total_weight = sum(weights)
        if total_weight == 0:
            return 120
        
        weighted_sum = sum(p * w for p, w in zip(predictions, weights))
        final_prediction = int(weighted_sum / total_weight)
        
        return max(30, final_prediction)
    
    def _get_time_of_day_based_prediction(self, time: datetime) -> int:
        hour = time.hour
        
        for period_name, (start, end, default_duration) in self.time_of_day_factors.items():
            if start <= hour <= end:
                return default_duration
        
        return 120
    
    def predict_upcoming_available_spots(self, occupied_spots: List[Dict], 
                                           hours_ahead: int = 2) -> List[SpotPrediction]:
        predictions = []
        
        for spot_data in occupied_spots:
            try:
                prediction = self.predict_exit_time(spot_data)
                
                if prediction.minutes_until_available <= hours_ahead * 60:
                    predictions.append(prediction)
            except Exception as e:
                logger.warning(f"预测车位 {spot_data.get('spot_number')} 失败: {e}")
                continue
        
        predictions.sort(key=lambda x: x.minutes_until_available)
        
        return predictions
    
    def get_peak_hour_prediction(self, current_time: datetime) -> PeakHourPrediction:
        is_peak = self.peak_detector.is_peak_hour(current_time)
        will_be_peak = self.peak_detector.will_be_peak_soon(current_time)
        
        next_peak = self.peak_detector.get_next_peak_time(current_time)
        
        expected_occupancy = self.peak_detector.predict_occupancy_rate(current_time)
        
        return PeakHourPrediction(
            is_peak_hour=is_peak,
            will_be_peak_soon=will_be_peak,
            peak_start_time=next_peak[0] if next_peak else None,
            peak_end_time=next_peak[1] if next_peak else None,
            expected_occupancy_rate=expected_occupancy,
            available_spots_estimate=int(100 * (1 - expected_occupancy))
        )
    
    def train_with_history(self, historical_records: List[Dict]):
        logger.info(f"开始训练模型，历史记录数: {len(historical_records)}")
        
        for record in historical_records:
            self.pattern_analyzer.add_parking_record(record)
        
        logger.info("模型训练完成")
    
    def save_model(self, filepath: str):
        model_data = {
            'historical_data': dict(self.pattern_analyzer.historical_data),
            'vehicle_patterns': dict(self.pattern_analyzer.vehicle_patterns),
            'saved_at': datetime.now().isoformat()
        }
        
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(model_data, f, ensure_ascii=False, indent=2, default=str)
        
        logger.info(f"模型已保存到: {filepath}")
    
    def load_model(self, filepath: str):
        if not os.path.exists(filepath):
            logger.warning(f"模型文件不存在: {filepath}")
            return
        
        with open(filepath, 'r', encoding='utf-8') as f:
            model_data = json.load(f)
        
        for plate, records in model_data.get('historical_data', {}).items():
            self.pattern_analyzer.historical_data[plate] = records
        
        for plate, durations in model_data.get('vehicle_patterns', {}).items():
            self.pattern_analyzer.vehicle_patterns[plate] = durations
        
        logger.info(f"模型已从 {filepath} 加载")


def predict_spot_exit(spot_data: Dict) -> SpotPrediction:
    predictor = SpotExitPredictor()
    return predictor.predict_exit_time(spot_data)


def predict_upcoming_spots(occupied_spots: List[Dict], hours_ahead: int = 2) -> List[SpotPrediction]:
    predictor = SpotExitPredictor()
    return predictor.predict_upcoming_available_spots(occupied_spots, hours_ahead)


def check_peak_hour(current_time: datetime = None) -> PeakHourPrediction:
    if current_time is None:
        current_time = datetime.now()
    
    predictor = SpotExitPredictor()
    return predictor.get_peak_hour_prediction(current_time)


if __name__ == "__main__":
    predictor = SpotExitPredictor()
    
    sample_spots = [
        {
            'spot_number': 'A001',
            'plate_number': '京A12345',
            'parking_lot_id': 1,
            'occupied_since': datetime.now() - timedelta(minutes=60),
            'zone': 'A'
        },
        {
            'spot_number': 'A002',
            'plate_number': '京B67890',
            'parking_lot_id': 1,
            'occupied_since': datetime.now() - timedelta(minutes=90),
            'zone': 'A'
        },
        {
            'spot_number': 'B001',
            'plate_number': '沪C11111',
            'parking_lot_id': 1,
            'occupied_since': datetime.now() - timedelta(minutes=30),
            'zone': 'B'
        }
    ]
    
    predictions = predictor.predict_upcoming_available_spots(sample_spots, hours_ahead=2)
    
    print("未来2小时即将可用的车位:")
    for pred in predictions:
        print(f"  车位: {pred.spot_number}, 车牌: {pred.plate_number}")
        print(f"    预计可用时间: {pred.predicted_exit_time}")
        print(f"    预计 {pred.minutes_until_available} 分钟后可用")
        print(f"    置信度: {pred.confidence:.2%}")
        print()
    
    peak_pred = predictor.get_peak_hour_prediction(datetime.now())
    print(f"高峰期状态:")
    print(f"  当前是否高峰期: {peak_pred.is_peak_hour}")
    print(f"  即将进入高峰期: {peak_pred.will_be_peak_soon}")
    print(f"  预计使用率: {peak_pred.expected_occupancy_rate:.1%}")
