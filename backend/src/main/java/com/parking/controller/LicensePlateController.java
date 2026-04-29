package com.parking.controller;

import com.parking.dto.ApiResponse;
import com.parking.dto.LicensePlateRecognitionResult;
import com.parking.entity.ParkingRecord;
import com.parking.service.LicensePlateService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/license-plate")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "车牌识别管理")
public class LicensePlateController {

    private final LicensePlateService licensePlateService;

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("识别车牌")
    public ApiResponse<LicensePlateRecognitionResult> recognizePlate(
            @ApiParam("车牌图片") @RequestParam("image") MultipartFile image,
            @ApiParam("闸机ID") @RequestParam(value = "gateId", defaultValue = "GATE_01") String gateId) {
        
        log.info("收到车牌识别请求，闸机: {}, 文件大小: {}", gateId, image.getSize());
        
        try {
            LicensePlateRecognitionResult result = licensePlateService.recognizePlate(image, gateId);
            
            if (result.isSuccess()) {
                return ApiResponse.success("识别成功", result);
            } else {
                return ApiResponse.error(result.getMessage());
            }
            
        } catch (Exception e) {
            log.error("车牌识别失败: {}", e.getMessage(), e);
            return ApiResponse.error("识别失败: " + e.getMessage());
        }
    }

    @PostMapping("/entry")
    @ApiOperation("处理车辆入场")
    public ApiResponse<ParkingRecord> processEntry(
            @RequestBody LicensePlateRecognitionResult recognitionResult,
            @RequestParam("parkingLotId") Long parkingLotId,
            @RequestParam(value = "gateId", defaultValue = "GATE_01") String gateId) {
        
        log.info("处理车辆入场: 车牌={}, 停车场={}", recognitionResult.getPlateNumber(), parkingLotId);
        
        try {
            ParkingRecord record = licensePlateService.processEntry(recognitionResult, parkingLotId, gateId);
            return ApiResponse.success("入场成功", record);
            
        } catch (IllegalStateException e) {
            log.warn("入场处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
            
        } catch (Exception e) {
            log.error("入场处理失败: {}", e.getMessage(), e);
            return ApiResponse.error("入场失败: " + e.getMessage());
        }
    }

    @PostMapping("/exit")
    @ApiOperation("处理车辆出场")
    public ApiResponse<ParkingRecord> processExit(
            @RequestBody LicensePlateRecognitionResult recognitionResult,
            @RequestParam("parkingLotId") Long parkingLotId,
            @RequestParam(value = "gateId", defaultValue = "GATE_02") String gateId) {
        
        log.info("处理车辆出场: 车牌={}, 停车场={}", recognitionResult.getPlateNumber(), parkingLotId);
        
        try {
            ParkingRecord record = licensePlateService.processExit(recognitionResult, parkingLotId, gateId);
            return ApiResponse.success("出场成功", record);
            
        } catch (IllegalStateException e) {
            log.warn("出场处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
            
        } catch (Exception e) {
            log.error("出场处理失败: {}", e.getMessage(), e);
            return ApiResponse.error("出场失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/entry-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("一体化入场处理（识别+入场）")
    public ApiResponse<ParkingRecord> processEntryWithImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("parkingLotId") Long parkingLotId,
            @RequestParam(value = "gateId", defaultValue = "GATE_01") String gateId) {
        
        log.info("一体化入场处理: 停车场={}, 闸机={}", parkingLotId, gateId);
        
        try {
            LicensePlateRecognitionResult recognitionResult = licensePlateService.recognizePlate(image, gateId);
            
            if (!recognitionResult.isSuccess()) {
                return ApiResponse.error("车牌识别失败: " + recognitionResult.getMessage());
            }
            
            ParkingRecord record = licensePlateService.processEntry(recognitionResult, parkingLotId, gateId);
            return ApiResponse.success("入场成功", record);
            
        } catch (IllegalStateException e) {
            log.warn("入场处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
            
        } catch (Exception e) {
            log.error("一体化入场处理失败: {}", e.getMessage(), e);
            return ApiResponse.error("处理失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/exit-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("一体化出场处理（识别+出场）")
    public ApiResponse<ParkingRecord> processExitWithImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("parkingLotId") Long parkingLotId,
            @RequestParam(value = "gateId", defaultValue = "GATE_02") String gateId) {
        
        log.info("一体化出场处理: 停车场={}, 闸机={}", parkingLotId, gateId);
        
        try {
            LicensePlateRecognitionResult recognitionResult = licensePlateService.recognizePlate(image, gateId);
            
            if (!recognitionResult.isSuccess()) {
                return ApiResponse.error("车牌识别失败: " + recognitionResult.getMessage());
            }
            
            ParkingRecord record = licensePlateService.processExit(recognitionResult, parkingLotId, gateId);
            return ApiResponse.success("出场成功", record);
            
        } catch (IllegalStateException e) {
            log.warn("出场处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
            
        } catch (Exception e) {
            log.error("一体化出场处理失败: {}", e.getMessage(), e);
            return ApiResponse.error("处理失败: " + e.getMessage());
        }
    }
}