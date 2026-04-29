package com.parking.service;

import com.parking.dto.LicensePlateRecognitionResult;
import com.parking.entity.ParkingRecord;
import com.parking.entity.ParkingSpot;
import com.parking.entity.Vehicle;
import com.parking.repository.ParkingRecordRepository;
import com.parking.repository.ParkingSpotRepository;
import com.parking.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class LicensePlateService {

    private final ParkingRecordRepository parkingRecordRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final VehicleRepository vehicleRepository;
    private final WebSocketService webSocketService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.license.plate.url:http://localhost:5000/api/recognize}")
    private String aiRecognitionUrl;

    private final Map<String, Long> activeEntrySessions = new ConcurrentHashMap<>();

    public LicensePlateRecognitionResult recognizePlate(MultipartFile image, String gateId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", image.getResource());
            body.add("gate_id", gateId);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<LicensePlateRecognitionResult> response = restTemplate.exchange(
                    aiRecognitionUrl,
                    HttpMethod.POST,
                    requestEntity,
                    LicensePlateRecognitionResult.class
            );

            LicensePlateRecognitionResult result = response.getBody();
            
            if (result != null && result.isSuccess() && result.getConfidence() >= 0.98) {
                log.info("车牌识别成功: {}, 置信度: {}", result.getPlateNumber(), result.getConfidence());
                return result;
            } else if (result != null) {
                log.warn("车牌识别置信度不足: {}, 置信度: {}", result.getPlateNumber(), result.getConfidence());
                result.setSuccess(false);
                result.setMessage("识别置信度不足，请重试");
                return result;
            } else {
                throw new RuntimeException("AI服务返回空结果");
            }

        } catch (Exception e) {
            log.error("车牌识别失败: {}", e.getMessage(), e);
            
            LicensePlateRecognitionResult result = new LicensePlateRecognitionResult();
            result.setSuccess(false);
            result.setMessage("识别服务暂时不可用: " + e.getMessage());
            return result;
        }
    }

    @Transactional
    public synchronized ParkingRecord processEntry(LicensePlateRecognitionResult recognitionResult, 
                                                   Long parkingLotId, 
                                                   String gateId) {
        String plateNumber = recognitionResult.getPlateNumber();
        
        Optional<ParkingRecord> existingActive = parkingRecordRepository.findActiveByPlateNumber(plateNumber);
        if (existingActive.isPresent()) {
            log.warn("车辆 {} 已在场内，禁止重复入场", plateNumber);
            throw new IllegalStateException("车辆已在场内");
        }

        synchronized (activeEntrySessions) {
            if (activeEntrySessions.containsKey(plateNumber)) {
                log.warn("车辆 {} 正在入场处理中", plateNumber);
                throw new IllegalStateException("车辆正在入场处理中，请稍候");
            }
            activeEntrySessions.put(plateNumber, System.currentTimeMillis());
        }

        try {
            List<ParkingSpot> availableSpots = parkingSpotRepository.findAvailableSpotsByParkingLotId(parkingLotId);
            if (availableSpots.isEmpty()) {
                throw new IllegalStateException("没有可用车位");
            }

            ParkingSpot assignedSpot = availableSpots.get(0);

            Vehicle vehicle = vehicleRepository.findByPlateNumber(plateNumber)
                    .orElseGet(() -> {
                        Vehicle newVehicle = new Vehicle();
                        newVehicle.setPlateNumber(plateNumber);
                        newVehicle.setVehicleType(recognitionResult.getVehicleType());
                        newVehicle.setColor(recognitionResult.getColor());
                        newVehicle.setStatus("ACTIVE");
                        return vehicleRepository.save(newVehicle);
                    });

            ParkingRecord record = new ParkingRecord();
            record.setPlateNumber(plateNumber);
            record.setVehicleType(vehicle.getVehicleType());
            record.setVehicleColor(vehicle.getColor());
            record.setEntryImageUrl(recognitionResult.getImageUrl());
            record.setEntryTime(LocalDateTime.now());
            record.setStatus("ACTIVE");
            record.setEntryGateId(gateId);

            record.setParkingSpot(assignedSpot);

            assignedSpot.setStatus("OCCUPIED");
            assignedSpot.setCurrentPlateNumber(plateNumber);
            assignedSpot.setOccupiedSince(LocalDateTime.now());
            parkingSpotRepository.save(assignedSpot);

            ParkingRecord savedRecord = parkingRecordRepository.save(record);

            webSocketService.sendSpotUpdate(parkingLotId, assignedSpot, "OCCUPIED");
            webSocketService.sendParkingLotUpdate(parkingLotId);

            log.info("车辆 {} 入场成功，分配车位: {}", plateNumber, assignedSpot.getSpotNumber());
            
            return savedRecord;

        } finally {
            activeEntrySessions.remove(plateNumber);
        }
    }

    @Transactional
    public synchronized ParkingRecord processExit(LicensePlateRecognitionResult recognitionResult, 
                                                  Long parkingLotId, 
                                                  String gateId) {
        String plateNumber = recognitionResult.getPlateNumber();

        ParkingRecord activeRecord = parkingRecordRepository.findActiveByPlateNumber(plateNumber)
                .orElseThrow(() -> new IllegalStateException("未找到该车辆的入场记录"));

        LocalDateTime exitTime = LocalDateTime.now();
        activeRecord.setExitTime(exitTime);
        activeRecord.setExitImageUrl(recognitionResult.getImageUrl());
        activeRecord.setExitGateId(gateId);

        long durationMinutes = java.time.Duration.between(activeRecord.getEntryTime(), exitTime).toMinutes();
        activeRecord.setDurationMinutes(durationMinutes);

        ParkingSpot spot = activeRecord.getParkingSpot();
        if (spot != null) {
            spot.setStatus("AVAILABLE");
            spot.setCurrentPlateNumber(null);
            spot.setOccupiedSince(null);
            spot.setEstimatedExitTime(null);
            parkingSpotRepository.save(spot);

            webSocketService.sendSpotUpdate(parkingLotId, spot, "AVAILABLE");
        }

        activeRecord.setStatus("COMPLETED");
        ParkingRecord savedRecord = parkingRecordRepository.save(activeRecord);

        webSocketService.sendParkingLotUpdate(parkingLotId);

        log.info("车辆 {} 出场成功，停车时长: {} 分钟", plateNumber, durationMinutes);

        return savedRecord;
    }
}