package com.parking.controller;

import com.parking.dto.ApiResponse;
import com.parking.dto.PredictionResult;
import com.parking.service.PredictionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "车位预测管理")
public class PredictionController {

    private final PredictionService predictionService;

    @GetMapping("/lots/{parkingLotId}/upcoming")
    @ApiOperation("获取即将可用的车位预测")
    public ApiResponse<List<PredictionResult>> getUpcomingAvailableSpots(
            @PathVariable Long parkingLotId,
            @RequestParam(defaultValue = "2") int hoursAhead) {
        
        log.info("获取未来 {} 小时可用车位预测: 停车场={}", hoursAhead, parkingLotId);
        
        List<PredictionResult> predictions = predictionService.getUpcomingAvailableSpots(parkingLotId, hoursAhead);
        return ApiResponse.success(predictions);
    }

    @GetMapping("/lots/{parkingLotId}/peak-status")
    @ApiOperation("判断是否为高峰期")
    public ApiResponse<java.util.Map<String, Object>> checkPeakStatus(
            @PathVariable Long parkingLotId) {
        
        LocalDateTime now = LocalDateTime.now();
        boolean isPeak = predictionService.isPeakHour(now);
        boolean willBePeak = predictionService.isPeakHour(now.plusMinutes(30));
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("isCurrentlyPeak", isPeak);
        result.put("willBePeakSoon", willBePeak);
        result.put("currentTime", now);
        result.put("peakHours", "工作日: 7:00-9:00, 17:00-19:00; 周末: 10:00-20:00");
        
        return ApiResponse.success(result);
    }

    @PostMapping("/lots/{parkingLotId}/trigger-update")
    @ApiOperation("手动触发预测更新")
    public ApiResponse<String> triggerPredictionUpdate(@PathVariable Long parkingLotId) {
        log.info("手动触发预测更新: 停车场={}", parkingLotId);
        
        predictionService.updatePredictions();
        predictionService.broadcastPredictions(parkingLotId);
        
        return ApiResponse.success("预测更新已触发");
    }

    @GetMapping("/lots/{parkingLotId}/next-available")
    @ApiOperation("获取下一个即将可用的车位")
    public ApiResponse<PredictionResult> getNextAvailableSpot(
            @PathVariable Long parkingLotId) {
        
        List<PredictionResult> predictions = predictionService.getUpcomingAvailableSpots(parkingLotId, 2);
        
        if (predictions.isEmpty()) {
            return ApiResponse.error("暂无即将可用的车位");
        }
        
        return ApiResponse.success(predictions.get(0));
    }

    @GetMapping("/lots/{parkingLotId}/available-soon")
    @ApiOperation("获取5分钟内即将可用的车位")
    public ApiResponse<List<PredictionResult>> getAvailableSoonSpots(
            @PathVariable Long parkingLotId) {
        
        List<PredictionResult> predictions = predictionService.getUpcomingAvailableSpots(parkingLotId, 2);
        
        List<PredictionResult> availableSoon = predictions.stream()
                .filter(p -> p.getMinutesUntilAvailable() <= 5)
                .toList();
        
        return ApiResponse.success(availableSoon);
    }
}