package com.parking.service;

import com.parking.dto.PredictionResult;
import com.parking.entity.ParkingRecord;
import com.parking.entity.ParkingSpot;
import com.parking.entity.SpotPrediction;
import com.parking.repository.ParkingRecordRepository;
import com.parking.repository.ParkingSpotRepository;
import com.parking.repository.SpotPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class PredictionService {

    private final SpotPredictionRepository predictionRepository;
    private final ParkingSpotRepository spotRepository;
    private final ParkingRecordRepository recordRepository;
    private final WebSocketService webSocketService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.prediction.url:http://localhost:5001/api/predict}")
    private String aiPredictionUrl;

    private final Map<String, List<Long>> historicalDurations = new ConcurrentHashMap<>();
    private final Map<DayOfWeek, Map<Integer, Integer>> dailyPatterns = new ConcurrentHashMap<>();

    @Scheduled(cron = "0 */5 * * * ?")
    public void updatePredictions() {
        log.info("开始更新车位预测数据");
        
        List<ParkingSpot> occupiedSpots = spotRepository.findByStatus("OCCUPIED");
        
        for (ParkingSpot spot : occupiedSpots) {
            try {
                predictExitTime(spot);
            } catch (Exception e) {
                log.error("预测车位 {} 离场时间失败: {}", spot.getSpotNumber(), e.getMessage());
            }
        }
        
        log.info("完成更新车位预测数据，共处理 {} 个占用车位", occupiedSpots.size());
    }

    public PredictionResult predictExitTime(ParkingSpot spot) {
        if (spot.getOccupiedSince() == null || spot.getCurrentPlateNumber() == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occupiedSince = spot.getOccupiedSince();
        long currentDurationMinutes = java.time.Duration.between(occupiedSince, now).toMinutes();

        List<Long> historicalTimes = getHistoricalDurations(spot.getCurrentPlateNumber());

        long predictedDurationMinutes = calculatePredictedDuration(
                spot.getCurrentPlateNumber(),
                currentDurationMinutes,
                historicalTimes,
                now
        );

        LocalDateTime predictedExitTime = occupiedSince.plusMinutes(predictedDurationMinutes);

        double confidence = calculateConfidence(historicalTimes, currentDurationMinutes, predictedDurationMinutes);

        spot.setEstimatedExitTime(predictedExitTime);
        spot.setConfidence(confidence);
        spotRepository.save(spot);

        SpotPrediction prediction = new SpotPrediction();
        prediction.setSpotNumber(spot.getSpotNumber());
        prediction.setParkingLotId(spot.getParkingLot().getId());
        prediction.setPlateNumber(spot.getCurrentPlateNumber());
        prediction.setOccupiedSince(spot.getOccupiedSince());
        prediction.setPredictedExitTime(predictedExitTime);
        prediction.setConfidence(confidence);
        prediction.setPredictionStatus("PENDING");
        prediction.setPredictionModel("HYBRID");
        predictionRepository.save(prediction);

        PredictionResult result = new PredictionResult();
        result.setSpotNumber(spot.getSpotNumber());
        result.setPlateNumber(spot.getCurrentPlateNumber());
        result.setPredictedExitTime(predictedExitTime);
        result.setConfidence(confidence);
        result.setCurrentDurationMinutes(currentDurationMinutes);

        return result;
    }

    private List<Long> getHistoricalDurations(String plateNumber) {
        List<ParkingRecord> records = recordRepository.findAllByPlateNumberOrderByEntryTimeDesc(plateNumber);
        
        List<Long> durations = new ArrayList<>();
        for (ParkingRecord record : records) {
            if (record.getDurationMinutes() != null && record.getDurationMinutes() > 0) {
                durations.add(record.getDurationMinutes());
            }
        }

        if (!durations.isEmpty()) {
            historicalDurations.put(plateNumber, durations);
        }

        return durations;
    }

    private long calculatePredictedDuration(String plateNumber, 
                                              long currentDuration,
                                              List<Long> historicalTimes,
                                              LocalDateTime now) {
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        int hour = now.getHour();

        long basePrediction = getTimeOfDayPattern(dayOfWeek, hour);

        if (!historicalTimes.isEmpty()) {
            double avgHistorical = historicalTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(basePrediction);

            if (historicalTimes.size() >= 3) {
                double weight = Math.min(0.7, historicalTimes.size() * 0.1);
                basePrediction = (long) (avgHistorical * weight + basePrediction * (1 - weight));
            }
        }

        if (currentDuration > basePrediction) {
            basePrediction = (long) (currentDuration * 1.2);
        }

        return Math.max(30, basePrediction);
    }

    private long getTimeOfDayPattern(DayOfWeek dayOfWeek, int hour) {
        if (hour >= 7 && hour < 9) {
            return 120;
        } else if (hour >= 12 && hour < 14) {
            return 90;
        } else if (hour >= 17 && hour < 19) {
            return 60;
        } else if (hour >= 22 || hour < 6) {
            return 480;
        } else {
            return 180;
        }
    }

    private double calculateConfidence(List<Long> historicalTimes, 
                                         long currentDuration,
                                         long predictedDuration) {
        double baseConfidence = 0.6;

        if (!historicalTimes.isEmpty()) {
            double variance = calculateVariance(historicalTimes);
            double consistencyScore = 1.0 / (1.0 + variance / 60.0);
            baseConfidence += consistencyScore * 0.2;
        }

        long diff = Math.abs(predictedDuration - currentDuration);
        if (diff < 30) {
            baseConfidence += 0.1;
        } else if (diff > 120) {
            baseConfidence -= 0.1;
        }

        return Math.min(0.99, Math.max(0.5, baseConfidence));
    }

    private double calculateVariance(List<Long> values) {
        if (values.size() < 2) {
            return 0;
        }
        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0);
        return values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
    }

    public List<PredictionResult> getUpcomingAvailableSpots(Long parkingLotId, int hoursAhead) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.plusHours(hoursAhead);

        List<SpotPrediction> predictions = predictionRepository.findPredictionsForNextTwoHours(
                parkingLotId, now, cutoffTime
        );

        List<PredictionResult> results = new ArrayList<>();
        for (SpotPrediction prediction : predictions) {
            if ("PENDING".equals(prediction.getPredictionStatus())) {
                PredictionResult result = new PredictionResult();
                result.setSpotNumber(prediction.getSpotNumber());
                result.setPlateNumber(prediction.getPlateNumber());
                result.setPredictedExitTime(prediction.getPredictedExitTime());
                result.setConfidence(prediction.getConfidence());

                long minutesUntilExit = java.time.Duration.between(now, prediction.getPredictedExitTime()).toMinutes();
                result.setMinutesUntilAvailable(Math.max(0, minutesUntilExit));

                results.add(result);
            }
        }

        results.sort(Comparator.comparing(PredictionResult::getMinutesUntilAvailable));

        return results;
    }

    public boolean isPeakHour(LocalDateTime time) {
        DayOfWeek day = time.getDayOfWeek();
        int hour = time.getHour();

        boolean isWeekday = day.getValue() >= 1 && day.getValue() <= 5;
        
        if (isWeekday) {
            return (hour >= 7 && hour < 9) || (hour >= 17 && hour < 19);
        } else {
            return hour >= 10 && hour < 20;
        }
    }

    public void broadcastPredictions(Long parkingLotId) {
        List<PredictionResult> predictions = getUpcomingAvailableSpots(parkingLotId, 2);
        webSocketService.sendPredictionUpdate(parkingLotId, predictions);
    }
}