#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
车位预测API服务
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
from datetime import datetime, timedelta
from typing import List, Dict
import logging
import os
import json

from spot_prediction import SpotExitPredictor, SpotPrediction, PeakHourPrediction

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

predictor = SpotExitPredictor()

MODEL_PATH = os.path.join(os.path.dirname(__file__), 'models', 'prediction_model.json')
os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)

def load_initial_model():
    if os.path.exists(MODEL_PATH):
        predictor.load_model(MODEL_PATH)
    else:
        sample_history = [
            {'plate_number': '京A12345', 'duration_minutes': 60, 'entry_time': '2024-01-01T08:00:00'},
            {'plate_number': '京A12345', 'duration_minutes': 90, 'entry_time': '2024-01-02T14:00:00'},
            {'plate_number': '京B67890', 'duration_minutes': 120, 'entry_time': '2024-01-01T09:00:00'},
            {'plate_number': '沪C11111', 'duration_minutes': 45, 'entry_time': '2024-01-01T17:00:00'},
        ]
        predictor.train_with_history(sample_history)
        predictor.save_model(MODEL_PATH)

load_initial_model()

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'service': 'spot-prediction',
        'timestamp': datetime.now().isoformat()
    })

@app.route('/api/predict', methods=['POST'])
def predict_single_spot():
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                'success': False,
                'message': '请提供车位数据'
            }), 400
        
        required_fields = ['spot_number', 'plate_number', 'occupied_since']
        for field in required_fields:
            if field not in data:
                return jsonify({
                    'success': False,
                    'message': f'缺少必填字段: {field}'
                }), 400
        
        prediction = predictor.predict_exit_time(data)
        
        response = {
            'success': True,
            'spotNumber': prediction.spot_number,
            'plateNumber': prediction.plate_number,
            'parkingLotId': prediction.parking_lot_id,
            'occupiedSince': prediction.occupied_since.isoformat(),
            'predictedExitTime': prediction.predicted_exit_time.isoformat(),
            'confidence': prediction.confidence,
            'currentDurationMinutes': prediction.current_duration_minutes,
            'minutesUntilAvailable': prediction.minutes_until_available,
            'zone': prediction.zone,
            'status': prediction.status,
            'timestamp': datetime.now().isoformat()
        }
        
        logger.info(f"预测完成 - 车位: {prediction.spot_number}, 车牌: {prediction.plate_number}, "
                   f"预计可用: {prediction.minutes_until_available}分钟后, 置信度: {prediction.confidence:.2%}")
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"预测错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'预测服务错误: {str(e)}'
        }), 500

@app.route('/api/predict/batch', methods=['POST'])
def predict_batch():
    try:
        data = request.get_json()
        
        if not data or 'spots' not in data:
            return jsonify({
                'success': False,
                'message': '请提供车位列表'
            }), 400
        
        spots = data['spots']
        hours_ahead = data.get('hours_ahead', 2)
        
        if not spots:
            return jsonify({
                'success': True,
                'total': 0,
                'predictions': []
            })
        
        predictions = predictor.predict_upcoming_available_spots(spots, hours_ahead)
        
        predictions_response = []
        for pred in predictions:
            predictions_response.append({
                'spotNumber': pred.spot_number,
                'plateNumber': pred.plate_number,
                'parkingLotId': pred.parking_lot_id,
                'predictedExitTime': pred.predicted_exit_time.isoformat(),
                'confidence': pred.confidence,
                'currentDurationMinutes': pred.current_duration_minutes,
                'minutesUntilAvailable': pred.minutes_until_available,
                'zone': pred.zone
            })
        
        response = {
            'success': True,
            'total': len(spots),
            'hoursAhead': hours_ahead,
            'upcomingCount': len(predictions_response),
            'predictions': predictions_response,
            'timestamp': datetime.now().isoformat()
        }
        
        logger.info(f"批量预测完成 - 总车位数: {len(spots)}, 即将可用: {len(predictions_response)}")
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"批量预测错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'批量预测服务错误: {str(e)}'
        }), 500

@app.route('/api/peak-status', methods=['GET'])
def get_peak_status():
    try:
        current_time_str = request.args.get('time')
        if current_time_str:
            current_time = datetime.fromisoformat(current_time_str.replace('Z', '+00:00'))
        else:
            current_time = datetime.now()
        
        peak_pred = predictor.get_peak_hour_prediction(current_time)
        
        response = {
            'success': True,
            'isCurrentlyPeak': peak_pred.is_peak_hour,
            'willBePeakSoon': peak_pred.will_be_peak_soon,
            'peakStartTime': peak_pred.peak_start_time.isoformat() if peak_pred.peak_start_time else None,
            'peakEndTime': peak_pred.peak_end_time.isoformat() if peak_pred.peak_end_time else None,
            'expectedOccupancyRate': peak_pred.expected_occupancy_rate,
            'availableSpotsEstimate': peak_pred.available_spots_estimate,
            'currentTime': current_time.isoformat(),
            'peakHoursDescription': {
                'weekday': '7:00-9:00, 17:00-19:00',
                'weekend': '10:00-20:00'
            }
        }
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"获取高峰期状态错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'获取高峰期状态错误: {str(e)}'
        }), 500

@app.route('/api/next-available', methods=['POST'])
def get_next_available():
    try:
        data = request.get_json()
        
        if not data or 'spots' not in data:
            return jsonify({
                'success': False,
                'message': '请提供车位列表'
            }), 400
        
        spots = data['spots']
        confidence_threshold = data.get('confidence_threshold', 0.7)
        
        predictions = predictor.predict_upcoming_available_spots(spots, hours_ahead=2)
        
        high_confidence = [p for p in predictions if p.confidence >= confidence_threshold]
        
        if not high_confidence:
            if predictions:
                next_spot = predictions[0]
            else:
                return jsonify({
                    'success': True,
                    'hasAvailableSoon': False,
                    'message': '暂无即将可用的车位'
                })
        else:
            next_spot = high_confidence[0]
        
        response = {
            'success': True,
            'hasAvailableSoon': True,
            'nextAvailable': {
                'spotNumber': next_spot.spot_number,
                'plateNumber': next_spot.plate_number,
                'minutesUntilAvailable': next_spot.minutes_until_available,
                'predictedExitTime': next_spot.predicted_exit_time.isoformat(),
                'confidence': next_spot.confidence,
                'zone': next_spot.zone
            },
            'totalUpcoming': len(predictions),
            'highConfidenceCount': len(high_confidence)
        }
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"获取下一个可用车位错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'获取下一个可用车位错误: {str(e)}'
        }), 500

@app.route('/api/available-soon', methods=['POST'])
def get_available_soon():
    try:
        data = request.get_json()
        
        if not data or 'spots' not in data:
            return jsonify({
                'success': False,
                'message': '请提供车位列表'
            }), 400
        
        spots = data['spots']
        minutes_threshold = data.get('minutes_threshold', 5)
        
        predictions = predictor.predict_upcoming_available_spots(spots, hours_ahead=2)
        
        available_soon = [p for p in predictions if p.minutes_until_available <= minutes_threshold]
        
        response = {
            'success': True,
            'minutesThreshold': minutes_threshold,
            'count': len(available_soon),
            'spots': []
        }
        
        for spot in available_soon:
            response['spots'].append({
                'spotNumber': spot.spot_number,
                'plateNumber': spot.plate_number,
                'minutesUntilAvailable': spot.minutes_until_available,
                'confidence': spot.confidence,
                'zone': spot.zone
            })
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"获取即将可用车位错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'获取即将可用车位错误: {str(e)}'
        }), 500

@app.route('/api/train', methods=['POST'])
def train_model():
    try:
        data = request.get_json()
        
        if not data or 'records' not in data:
            return jsonify({
                'success': False,
                'message': '请提供历史记录'
            }), 400
        
        records = data['records']
        
        predictor.train_with_history(records)
        predictor.save_model(MODEL_PATH)
        
        response = {
            'success': True,
            'message': f'模型训练完成，共处理 {len(records)} 条记录',
            'recordsProcessed': len(records),
            'modelSaved': True,
            'modelPath': MODEL_PATH
        }
        
        logger.info(f"模型训练完成，处理 {len(records)} 条记录")
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"模型训练错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'模型训练错误: {str(e)}'
        }), 500

@app.route('/api/model/status', methods=['GET'])
def get_model_status():
    try:
        has_model = os.path.exists(MODEL_PATH)
        
        response = {
            'success': True,
            'modelExists': has_model,
            'modelPath': MODEL_PATH,
            'features': [
                '历史停车模式分析',
                '时间特征分析',
                '车辆行为模式识别',
                '高峰期检测',
                '置信度评估'
            ]
        }
        
        if has_model:
            stat = os.stat(MODEL_PATH)
            response['modelModifiedTime'] = datetime.fromtimestamp(stat.st_mtime).isoformat()
            response['modelSizeBytes'] = stat.st_size
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"获取模型状态错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'获取模型状态错误: {str(e)}'
        }), 500

@app.route('/api/info', methods=['GET'])
def get_info():
    return jsonify({
        'service': 'Spot Prediction API',
        'version': '1.0.0',
        'description': '车位预测系统 - 预测未来2小时哪些车位会空出来，高峰期提前推送提醒',
        'endpoints': {
            'POST /api/predict': '预测单个车位离场时间',
            'POST /api/predict/batch': '批量预测车位离场时间',
            'GET /api/peak-status': '获取高峰期状态',
            'POST /api/next-available': '获取下一个即将可用的车位',
            'POST /api/available-soon': '获取即将可用的车位列表',
            'POST /api/train': '训练模型',
            'GET /api/model/status': '获取模型状态',
            'GET /health': '健康检查'
        },
        'prediction_horizon': '未来2小时',
        'confidence_threshold': '可配置，默认70%'
    })

if __name__ == '__main__':
    logger.info("启动车位预测服务...")
    app.run(host='0.0.0.0', port=5001, debug=True, threaded=True)
