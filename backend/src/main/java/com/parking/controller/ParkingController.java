package com.parking.controller;

import com.parking.dto.ApiResponse;
import com.parking.dto.ParkingLotStatus;
import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingSpot;
import com.parking.service.ParkingService;
import com.parking.service.PredictionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/parking")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "停车场管理")
public class ParkingController {

    private final ParkingService parkingService;
    private final PredictionService predictionService;

    @GetMapping("/lots")
    @ApiOperation("获取所有停车场")
    public ApiResponse<List<ParkingLot>> getAllParkingLots() {
        List<ParkingLot> lots = parkingService.getAllParkingLots();
        return ApiResponse.success(lots);
    }

    @GetMapping("/lots/{id}")
    @ApiOperation("获取停车场详情")
    public ApiResponse<ParkingLot> getParkingLotById(@PathVariable Long id) {
        return parkingService.getParkingLotById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("停车场不存在"));
    }

    @PostMapping("/lots")
    @ApiOperation("创建停车场")
    public ApiResponse<ParkingLot> createParkingLot(@RequestBody ParkingLot parkingLot) {
        try {
            ParkingLot saved = parkingService.createParkingLot(parkingLot);
            return ApiResponse.success("创建成功", saved);
        } catch (Exception e) {
            log.error("创建停车场失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    @GetMapping("/lots/{id}/status")
    @ApiOperation("获取停车场实时状态")
    public ApiResponse<ParkingLotStatus> getParkingLotStatus(@PathVariable Long id) {
        return parkingService.getParkingLotById(id)
                .map(lot -> {
                    ParkingLotStatus status = new ParkingLotStatus();
                    status.setParkingLotId(lot.getId());
                    status.setParkingLotName(lot.getName());
                    status.setTotalSpots(lot.getTotalSpots());
                    status.setOccupiedSpots(lot.getOccupiedSpots());
                    status.setAvailableSpots(lot.getAvailableSpots());
                    status.setOccupancyRate((double) lot.getOccupiedSpots() / lot.getTotalSpots());
                    status.setLastUpdated(LocalDateTime.now());
                    status.setPeakHour(predictionService.isPeakHour(LocalDateTime.now()));
                    status.setUpcomingAvailableSpots(predictionService.getUpcomingAvailableSpots(id, 2));
                    
                    return ApiResponse.success(status);
                })
                .orElseGet(() -> ApiResponse.error("停车场不存在"));
    }

    @GetMapping("/lots/{id}/spots")
    @ApiOperation("获取停车场所有车位")
    public ApiResponse<List<ParkingSpot>> getParkingSpots(@PathVariable Long id) {
        List<ParkingSpot> spots = parkingService.getSpotsByParkingLot(id);
        return ApiResponse.success(spots);
    }

    @GetMapping("/lots/{id}/spots/available")
    @ApiOperation("获取可用车位")
    public ApiResponse<List<ParkingSpot>> getAvailableSpots(@PathVariable Long id) {
        List<ParkingSpot> spots = parkingService.getAvailableSpots(id);
        return ApiResponse.success(spots);
    }

    @GetMapping("/lots/{id}/spots/count")
    @ApiOperation("获取车位数量统计")
    public ApiResponse<java.util.Map<String, Object>> getSpotCounts(@PathVariable Long id) {
        Long available = parkingService.countAvailableSpots(id);
        Long occupied = parkingService.countOccupiedSpots(id);
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("available", available);
        result.put("occupied", occupied);
        result.put("total", available + occupied);
        
        return ApiResponse.success(result);
    }

    @PutMapping("/spots/{spotId}/status")
    @ApiOperation("更新车位状态")
    public ApiResponse<ParkingSpot> updateSpotStatus(
            @PathVariable Long spotId,
            @RequestParam String status) {
        try {
            ParkingSpot spot = parkingService.updateSpotStatus(spotId, status);
            return ApiResponse.success("状态更新成功", spot);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新车位状态失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/lots/{id}/broadcast")
    @ApiOperation("广播停车场状态")
    public ApiResponse<Void> broadcastParkingLotStatus(@PathVariable Long id) {
        parkingService.broadcastParkingLotStatus(id);
        predictionService.broadcastPredictions(id);
        return ApiResponse.success(null);
    }
}