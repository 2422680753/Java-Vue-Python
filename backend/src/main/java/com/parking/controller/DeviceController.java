package com.parking.controller;

import com.parking.device.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final DeviceMonitoringService monitoringService;
    private final DeviceFailoverService failoverService;
    private final DeviceAlertService alertService;

    @GetMapping
    public ResponseEntity<List<Device>> getAllDevices(
            @RequestParam(required = false) Device.DeviceType type,
            @RequestParam(required = false) Device.DeviceStatus status,
            @RequestParam(required = false) Device.DeviceHealth health,
            @RequestParam(required = false) Long parkingLotId) {
        
        List<Device> devices;
        
        if (type != null && status != null && health != null && parkingLotId != null) {
            devices = deviceRepository.findHealthyDevicesByLotAndType(parkingLotId, type, status, health);
        } else if (type != null && status != null) {
            devices = deviceRepository.findByTypeAndStatus(type, status);
        } else if (type != null && health != null) {
            devices = deviceRepository.findByTypeAndHealth(type, health);
        } else if (type != null) {
            devices = deviceRepository.findByType(type);
        } else if (status != null) {
            devices = deviceRepository.findByStatus(status);
        } else if (health != null) {
            devices = deviceRepository.findByHealth(health);
        } else if (parkingLotId != null) {
            devices = deviceRepository.findByParkingLotId(parkingLotId);
        } else {
            devices = deviceRepository.findAll();
        }
        
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<Device> getDevice(@PathVariable String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Device> registerDevice(@RequestBody Device device) {
        Device saved = monitoringService.registerDevice(device);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{deviceId}")
    public ResponseEntity<Device> updateDevice(@PathVariable String deviceId, @RequestBody Device device) {
        Optional<Device> existingOpt = deviceRepository.findByDeviceId(deviceId);
        
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Device existing = existingOpt.get();
        
        if (device.getName() != null) existing.setName(device.getName());
        if (device.getType() != null) existing.setType(device.getType());
        if (device.getLocation() != null) existing.setLocation(device.getLocation());
        if (device.getParkingLotId() != null) existing.setParkingLotId(device.getParkingLotId());
        if (device.getGateId() != null) existing.setGateId(device.getGateId());
        if (device.getStatus() != null) existing.setStatus(device.getStatus());
        if (device.getHealth() != null) existing.setHealth(device.getHealth());
        if (device.getIpAddress() != null) existing.setIpAddress(device.getIpAddress());
        if (device.getMacAddress() != null) existing.setMacAddress(device.getMacAddress());
        if (device.getEnabled() != null) existing.setEnabled(device.getEnabled());
        if (device.getBackupDeviceId() != null) existing.setBackupDeviceId(device.getBackupDeviceId());
        if (device.getPriority() != null) existing.setPriority(device.getPriority());
        if (device.getRole() != null) existing.setRole(device.getRole());
        if (device.getDescription() != null) existing.setDescription(device.getDescription());
        
        Device updated = deviceRepository.save(existing);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable String deviceId) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        deviceRepository.delete(deviceOpt.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deviceId}/heartbeat")
    public ResponseEntity<Map<String, Object>> receiveHeartbeat(
            @PathVariable String deviceId,
            @RequestBody(required = false) Map<String, Object> payload) {
        
        Device.DeviceStatus status = Device.DeviceStatus.ONLINE;
        Device.DeviceHealth health = Device.DeviceHealth.HEALTHY;
        String statusMessage = null;
        Long responseTimeMs = null;
        
        DeviceHeartbeatLog.HeartbeatMetrics metrics = null;
        
        if (payload != null) {
            if (payload.containsKey("status")) {
                try {
                    status = Device.DeviceStatus.valueOf((String) payload.get("status"));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid device status: {}", payload.get("status"));
                }
            }
            
            if (payload.containsKey("health")) {
                try {
                    health = Device.DeviceHealth.valueOf((String) payload.get("health"));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid device health: {}", payload.get("health"));
                }
            }
            
            if (payload.containsKey("statusMessage")) {
                statusMessage = (String) payload.get("statusMessage");
            }
            
            if (payload.containsKey("responseTimeMs")) {
                responseTimeMs = ((Number) payload.get("responseTimeMs")).longValue();
            }
            
            metrics = DeviceHeartbeatLog.HeartbeatMetrics.builder()
                .cpuUsage(getIntegerFromPayload(payload, "cpuUsage"))
                .memoryUsage(getIntegerFromPayload(payload, "memoryUsage"))
                .diskUsage(getIntegerFromPayload(payload, "diskUsage"))
                .networkLatency(getIntegerFromPayload(payload, "networkLatency"))
                .temperature(getDoubleFromPayload(payload, "temperature"))
                .batteryLevel(getIntegerFromPayload(payload, "batteryLevel"))
                .build();
        }
        
        DeviceHeartbeatLog heartbeat = monitoringService.processHeartbeat(
            deviceId, status, health, metrics, statusMessage, responseTimeMs);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deviceId", deviceId);
        response.put("receivedAt", LocalDateTime.now());
        response.put("status", status);
        response.put("health", health);
        
        if (heartbeat != null) {
            response.put("heartbeatId", heartbeat.getId());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deviceId}/error")
    public ResponseEntity<Map<String, Object>> reportError(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> payload) {
        
        String errorMessage = payload.getOrDefault("error", "Unknown error");
        
        monitoringService.markDeviceError(deviceId, errorMessage);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deviceId", deviceId);
        response.put("errorRecorded", true);
        response.put("errorMessage", errorMessage);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{deviceId}/stats")
    public ResponseEntity<Map<String, Object>> getDeviceStats(@PathVariable String deviceId) {
        Map<String, Object> stats = monitoringService.getDeviceStats(deviceId);
        Map<String, Object> alertStats = alertService.getAlertStats(deviceId);
        
        stats.putAll(alertStats);
        
        if (failoverService.isInFailover(deviceId)) {
            DeviceFailoverService.FailoverRecord record = failoverService.getFailoverRecord(deviceId);
            Map<String, Object> failoverInfo = new HashMap<>();
            failoverInfo.put("isInFailover", true);
            failoverInfo.put("backupDeviceId", record.getBackupDeviceId());
            failoverInfo.put("backupDeviceName", record.getBackupDeviceName());
            failoverInfo.put("failoverTime", record.getFailoverTime());
            stats.put("failover", failoverInfo);
        } else {
            stats.put("failover", Map.of("isInFailover", false));
        }
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{deviceId}/recover")
    public ResponseEntity<Map<String, Object>> recoverDevice(@PathVariable String deviceId) {
        boolean success = failoverService.recoverDevice(deviceId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("deviceId", deviceId);
        
        if (success) {
            response.put("message", "Device recovery initiated successfully");
        } else {
            response.put("message", "Device not eligible for recovery or no active failover");
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/failover/status")
    public ResponseEntity<Map<String, Object>> getFailoverStatus() {
        return ResponseEntity.ok(failoverService.getFailoverStats());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        long totalDevices = deviceRepository.count();
        long onlineDevices = deviceRepository.countByTypeAndHealth(Device.DeviceType.CAMERA, Device.DeviceHealth.HEALTHY);
        long criticalDevices = deviceRepository.countByTypeAndHealth(Device.DeviceType.CAMERA, Device.DeviceHealth.CRITICAL);
        long degradedDevices = deviceRepository.countByTypeAndHealth(Device.DeviceType.CAMERA, Device.DeviceHealth.DEGRADED);
        
        health.put("totalDevices", totalDevices);
        health.put("onlineDevices", onlineDevices);
        health.put("criticalDevices", criticalDevices);
        health.put("degradedDevices", degradedDevices);
        
        Map<String, Object> failover = failoverService.getFailoverStats();
        health.put("failover", failover);
        
        String overallStatus = "HEALTHY";
        if (criticalDevices > 0) {
            overallStatus = "CRITICAL";
        } else if (degradedDevices > 0) {
            overallStatus = "DEGRADED";
        }
        
        health.put("overallStatus", overallStatus);
        health.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(health);
    }

    @PostMapping("/{deviceId}/test-failover")
    public ResponseEntity<Map<String, Object>> testFailover(@PathVariable String deviceId) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Device device = deviceOpt.get();
        boolean success = failoverService.triggerFailover(device);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("deviceId", deviceId);
        response.put("testMode", true);
        
        if (success) {
            response.put("message", "Test failover completed successfully");
        } else {
            response.put("message", "No backup devices available for test failover");
        }
        
        return ResponseEntity.ok(response);
    }

    private Integer getIntegerFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private Double getDoubleFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
}
