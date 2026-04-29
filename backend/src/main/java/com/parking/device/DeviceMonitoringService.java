package com.parking.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceMonitoringService {

    private final DeviceRepository deviceRepository;
    private final DeviceHeartbeatRepository heartbeatRepository;
    private final DeviceAlertService alertService;
    private final DeviceFailoverService failoverService;

    private static final int HEARTBEAT_TIMEOUT_SECONDS = 5;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int RESPONSE_TIME_WARNING_MS = 2000;
    private static final int RESPONSE_TIME_CRITICAL_MS = 5000;

    private final Map<String, DeviceStatusInfo> deviceStatusMap = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> deviceResponseTimes = new ConcurrentHashMap<>();

    @Transactional
    public Device registerDevice(Device device) {
        Optional<Device> existing = deviceRepository.findByDeviceId(device.getDeviceId());
        
        if (existing.isPresent()) {
            Device existingDevice = existing.get();
            existingDevice.setName(device.getName());
            existingDevice.setType(device.getType());
            existingDevice.setLocation(device.getLocation());
            existingDevice.setParkingLotId(device.getParkingLotId());
            existingDevice.setGateId(device.getGateId());
            existingDevice.setIpAddress(device.getIpAddress());
            existingDevice.setMacAddress(device.getMacAddress());
            existingDevice.setModel(device.getModel());
            existingDevice.setFirmwareVersion(device.getFirmwareVersion());
            existingDevice.setHeartbeatInterval(device.getHeartbeatInterval());
            existingDevice.setBackupDeviceId(device.getBackupDeviceId());
            existingDevice.setPriority(device.getPriority());
            existingDevice.setRole(device.getRole());
            existingDevice.setConfigJson(device.getConfigJson());
            existingDevice.setDescription(device.getDescription());
            
            log.info("Updated existing device: {}", device.getDeviceId());
            return deviceRepository.save(existingDevice);
        }
        
        log.info("Registered new device: {}", device.getDeviceId());
        return deviceRepository.save(device);
    }

    @Transactional
    public DeviceHeartbeatLog processHeartbeat(String deviceId, 
                                                 Device.DeviceStatus status,
                                                 Device.DeviceHealth health,
                                                 DeviceHeartbeatLog.HeartbeatMetrics metrics,
                                                 String statusMessage,
                                                 Long responseTimeMs) {
        
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        
        if (deviceOpt.isEmpty()) {
            log.warn("Heartbeat received for unknown device: {}", deviceId);
            return null;
        }
        
        Device device = deviceOpt.get();
        
        DeviceHeartbeatLog.HeartbeatLogBuilder logBuilder = DeviceHeartbeatLog.builder()
            .deviceId(deviceId)
            .heartbeatTime(LocalDateTime.now())
            .status(status)
            .health(health)
            .statusMessage(statusMessage)
            .responseTimeMs(responseTimeMs)
            .success(status != Device.DeviceStatus.ERROR);
        
        if (metrics != null) {
            logBuilder.cpuUsage(metrics.getCpuUsage())
                .memoryUsage(metrics.getMemoryUsage())
                .diskUsage(metrics.getDiskUsage())
                .networkLatency(metrics.getNetworkLatency())
                .temperature(metrics.getTemperature())
                .batteryLevel(metrics.getBatteryLevel());
        }
        
        DeviceHeartbeatLog heartbeat = logBuilder.build();
        heartbeat = heartbeatRepository.save(heartbeat);
        
        updateDeviceStatus(device, status, health, metrics, responseTimeMs);
        trackResponseTime(deviceId, responseTimeMs);
        
        DeviceStatusInfo statusInfo = getOrCreateStatusInfo(deviceId);
        statusInfo.recordSuccess(status, health, heartbeat.getHeartbeatTime());
        
        log.debug("Processed heartbeat for device: {}, status: {}, health: {}, response: {}ms",
            deviceId, status, health, responseTimeMs);
        
        return heartbeat;
    }

    @Transactional
    public void markDeviceError(String deviceId, String errorMessage) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        
        if (deviceOpt.isEmpty()) {
            log.warn("Cannot mark unknown device as error: {}", deviceId);
            return;
        }
        
        Device device = deviceOpt.get();
        
        DeviceStatusInfo statusInfo = getOrCreateStatusInfo(deviceId);
        statusInfo.recordFailure(errorMessage);
        
        int consecutiveFailures = statusInfo.getConsecutiveFailures();
        log.warn("Device {} error recorded (consecutive: {}): {}", 
            deviceId, consecutiveFailures, errorMessage);
        
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            handleDeviceFailure(device, errorMessage, consecutiveFailures);
        } else {
            device.updateHealth(Device.DeviceHealth.DEGRADED);
            device.recordError(errorMessage);
            deviceRepository.save(device);
            
            alertService.sendDeviceWarningAlert(device, "连续失败 " + consecutiveFailures + 
                " 次，即将触发故障切换: " + errorMessage);
        }
    }

    @Transactional
    public void handleDeviceFailure(Device device, String errorMessage, int failureCount) {
        String deviceId = device.getDeviceId();
        
        log.error("Device failure detected: {}, failures: {}, error: {}", 
            deviceId, failureCount, errorMessage);
        
        device.updateStatus(Device.DeviceStatus.OFFLINE);
        device.updateHealth(Device.DeviceHealth.CRITICAL);
        device.recordError(errorMessage);
        deviceRepository.save(device);
        
        DeviceStatusInfo statusInfo = getOrCreateStatusInfo(deviceId);
        statusInfo.setFailed(true);
        
        alertService.sendDeviceCriticalAlert(device, "设备故障 - 连续失败 " + failureCount + 
            " 次，最后错误: " + errorMessage);
        
        failoverService.triggerFailover(device);
    }

    @Scheduled(fixedRate = 1000)
    public void monitorDeviceStatus() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutThreshold = now.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS * 2);
        
        List<Device> allDevices = deviceRepository.findByEnabledTrue();
        
        for (Device device : allDevices) {
            if (device.getLastHeartbeatTime() == null) {
                if (Device.DeviceStatus.ONLINE.equals(device.getStatus())) {
                    log.warn("Device {} has never sent a heartbeat but marked as ONLINE", device.getDeviceId());
                    checkAndMarkStale(device);
                }
                continue;
            }
            
            if (device.getLastHeartbeatTime().isBefore(timeoutThreshold)) {
                checkAndMarkStale(device);
            }
        }
    }

    @Transactional
    protected void checkAndMarkStale(Device device) {
        String deviceId = device.getDeviceId();
        
        if (Device.DeviceStatus.OFFLINE.equals(device.getStatus())) {
            return;
        }
        
        DeviceStatusInfo statusInfo = getOrCreateStatusInfo(deviceId);
        
        if (!statusInfo.isWarned() && !statusInfo.isFailed()) {
            statusInfo.setWarned(true);
            
            device.updateHealth(Device.DeviceHealth.DEGRADED);
            deviceRepository.save(device);
            
            log.warn("Device {} heartbeat timeout - sending warning", deviceId);
            alertService.sendDeviceWarningAlert(device, "心跳超时超过 " + 
                HEARTBEAT_TIMEOUT_SECONDS + " 秒，设备可能异常");
        } else if (!statusInfo.isFailed()) {
            int staleCount = statusInfo.incrementStaleCount();
            
            if (staleCount >= 3) {
                handleDeviceFailure(device, "心跳超时 - 超过 " + 
                    (HEARTBEAT_TIMEOUT_SECONDS * staleCount) + " 秒无响应", staleCount);
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupOldHeartbeats() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        heartbeatRepository.deleteByHeartbeatTimeBefore(cutoff);
        log.debug("Cleaned up heartbeats older than 7 days");
    }

    private void updateDeviceStatus(Device device, 
                                      Device.DeviceStatus status,
                                      Device.DeviceHealth health,
                                      DeviceHeartbeatLog.HeartbeatMetrics metrics,
                                      Long responseTimeMs) {
        
        device.setLastHeartbeatTime(LocalDateTime.now());
        
        if (!device.getStatus().equals(status)) {
            device.updateStatus(status);
            log.info("Device {} status changed from {} to {}", 
                device.getDeviceId(), device.getStatus(), status);
        }
        
        if (!device.getHealth().equals(health)) {
            device.updateHealth(health);
            log.info("Device {} health changed from {} to {}", 
                device.getDeviceId(), device.getHealth(), health);
        }
        
        if (responseTimeMs != null && responseTimeMs > RESPONSE_TIME_CRITICAL_MS) {
            log.warn("Device {} response time critical: {}ms", device.getDeviceId(), responseTimeMs);
        } else if (responseTimeMs != null && responseTimeMs > RESPONSE_TIME_WARNING_MS) {
            log.debug("Device {} response time elevated: {}ms", device.getDeviceId(), responseTimeMs);
        }
        
        deviceRepository.save(device);
    }

    private void trackResponseTime(String deviceId, Long responseTimeMs) {
        if (responseTimeMs == null) {
            return;
        }
        
        deviceResponseTimes.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>());
        
        List<Long> times = deviceResponseTimes.get(deviceId);
        times.add(responseTimeMs);
        
        while (times.size() > 100) {
            times.remove(0);
        }
    }

    public DeviceStatusInfo getOrCreateStatusInfo(String deviceId) {
        return deviceStatusMap.computeIfAbsent(deviceId, k -> new DeviceStatusInfo());
    }

    public DeviceStatusInfo getDeviceStatusInfo(String deviceId) {
        return deviceStatusMap.get(deviceId);
    }

    public Map<String, Object> getDeviceStats(String deviceId) {
        Map<String, Object> stats = new HashMap<>();
        
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        deviceOpt.ifPresent(device -> {
            stats.put("deviceId", device.getDeviceId());
            stats.put("name", device.getName());
            stats.put("type", device.getType());
            stats.put("status", device.getStatus());
            stats.put("health", device.getHealth());
            stats.put("lastHeartbeat", device.getLastHeartbeatTime());
            stats.put("errorCount", device.getErrorCount());
            stats.put("lastError", device.getLastError());
        });
        
        DeviceStatusInfo statusInfo = deviceStatusMap.get(deviceId);
        if (statusInfo != null) {
            stats.put("consecutiveFailures", statusInfo.getConsecutiveFailures());
            stats.put("consecutiveSuccesses", statusInfo.getConsecutiveSuccesses());
            stats.put("isFailed", statusInfo.isFailed());
            stats.put("isWarned", statusInfo.isWarned());
        }
        
        List<Long> responseTimes = deviceResponseTimes.get(deviceId);
        if (responseTimes != null && !responseTimes.isEmpty()) {
            double avg = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long min = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
            
            stats.put("avgResponseTimeMs", avg);
            stats.put("maxResponseTimeMs", max);
            stats.put("minResponseTimeMs", min);
            stats.put("sampleCount", responseTimes.size());
        }
        
        return stats;
    }

    @Data
    public static class DeviceStatusInfo {
        private int consecutiveFailures = 0;
        private int consecutiveSuccesses = 0;
        private int staleCount = 0;
        private boolean failed = false;
        private boolean warned = false;
        private LocalDateTime lastSuccessTime;
        private LocalDateTime lastFailureTime;
        private Device.DeviceStatus lastStatus;
        private Device.DeviceHealth lastHealth;
        private String lastErrorMessage;

        public void recordSuccess(Device.DeviceStatus status, Device.DeviceHealth health, LocalDateTime time) {
            this.consecutiveFailures = 0;
            this.consecutiveSuccesses++;
            this.staleCount = 0;
            this.lastStatus = status;
            this.lastHealth = health;
            this.lastSuccessTime = time;
        }

        public void recordFailure(String errorMessage) {
            this.consecutiveSuccesses = 0;
            this.consecutiveFailures++;
            this.lastErrorMessage = errorMessage;
            this.lastFailureTime = LocalDateTime.now();
        }

        public int incrementStaleCount() {
            return ++this.staleCount;
        }
    }
}
