package com.parking.device;

import com.parking.entity.Notification;
import com.parking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceAlertService {

    private final NotificationService notificationService;
    
    private static final int ALERT_COOLDOWN_MINUTES = 5;
    
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> alertCount = new ConcurrentHashMap<>();

    public void sendDeviceWarningAlert(Device device, String message) {
        sendAlert(device, AlertLevel.WARNING, message);
    }

    public void sendDeviceCriticalAlert(Device device, String message) {
        sendAlert(device, AlertLevel.CRITICAL, message);
    }

    public void sendDeviceInfoAlert(Device device, String message) {
        sendAlert(device, AlertLevel.INFO, message);
    }

    public void sendDeviceRecoveredAlert(Device device, String message) {
        sendAlert(device, AlertLevel.RECOVERED, message);
    }

    private void sendAlert(Device device, AlertLevel level, String message) {
        String deviceId = device.getDeviceId();
        String alertKey = deviceId + ":" + level;
        
        if (!shouldSendAlert(alertKey, level)) {
            log.debug("Alert suppressed for device {} due to cooldown: {}", deviceId, message);
            return;
        }
        
        updateAlertTracking(alertKey);
        
        String title = buildAlertTitle(device, level);
        String fullMessage = buildAlertMessage(device, level, message);
        
        logAlert(device, level, fullMessage);
        
        try {
            Notification notification = createNotification(device, title, fullMessage, level);
            notificationService.sendNotification(notification);
            log.info("Device alert sent - level: {}, device: {}, message: {}", 
                level, deviceId, fullMessage);
        } catch (Exception e) {
            log.error("Failed to send device alert: {}", e.getMessage(), e);
        }
    }

    private boolean shouldSendAlert(String alertKey, AlertLevel level) {
        LocalDateTime lastTime = lastAlertTime.get(alertKey);
        
        if (lastTime == null) {
            return true;
        }
        
        LocalDateTime cooldownEnd = lastTime.plusMinutes(ALERT_COOLDOWN_MINUTES);
        if (LocalDateTime.now().isAfter(cooldownEnd)) {
            return true;
        }
        
        if (AlertLevel.CRITICAL.equals(level)) {
            int count = alertCount.getOrDefault(alertKey, 0);
            return count % 3 == 0;
        }
        
        return false;
    }

    private void updateAlertTracking(String alertKey) {
        LocalDateTime now = LocalDateTime.now();
        lastAlertTime.put(alertKey, now);
        alertCount.merge(alertKey, 1, Integer::sum);
    }

    private String buildAlertTitle(Device device, AlertLevel level) {
        String prefix = switch (level) {
            case INFO -> "[设备信息]";
            case WARNING -> "[设备告警]";
            case CRITICAL -> "[严重告警]";
            case RECOVERED -> "[设备恢复]";
        };
        
        return prefix + " " + device.getName() + " (" + device.getDeviceId() + ")";
    }

    private String buildAlertMessage(Device device, AlertLevel level, String message) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("设备类型: ").append(device.getType().getLabel()).append("\n");
        sb.append("设备位置: ").append(Optional.ofNullable(device.getLocation()).orElse("未设置")).append("\n");
        sb.append("当前状态: ").append(device.getStatus().getLabel()).append("\n");
        sb.append("健康状态: ").append(device.getHealth().getLabel()).append("\n");
        
        if (device.getLastError() != null) {
            sb.append("错误信息: ").append(device.getLastError()).append("\n");
        }
        
        if (device.getErrorCount() > 0) {
            sb.append("累计错误: ").append(device.getErrorCount()).append(" 次\n");
        }
        
        sb.append("\n详细信息: ").append(message);
        
        if (device.getIpAddress() != null) {
            sb.append("\n设备IP: ").append(device.getIpAddress());
        }
        
        sb.append("\n\n告警时间: ").append(LocalDateTime.now());
        
        return sb.toString();
    }

    private void logAlert(Device device, AlertLevel level, String message) {
        switch (level) {
            case CRITICAL -> log.error("DEVICE CRITICAL ALERT - {}: {}", device.getDeviceId(), message);
            case WARNING -> log.warn("DEVICE WARNING - {}: {}", device.getDeviceId(), message);
            case INFO -> log.info("DEVICE INFO - {}: {}", device.getDeviceId(), message);
            case RECOVERED -> log.info("DEVICE RECOVERED - {}: {}", device.getDeviceId(), message);
        }
    }

    private Notification createNotification(Device device, String title, String message, AlertLevel level) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(message);
        notification.setType("DEVICE_ALERT");
        notification.setPriority(mapToNotificationPriority(level));
        notification.setStatus("PENDING");
        notification.setChannel(mapToNotificationChannel(level));
        
        if (device.getParkingLotId() != null) {
            notification.setTargetUserId(device.getParkingLotId());
        }
        
        return notification;
    }

    private String mapToNotificationPriority(AlertLevel level) {
        return switch (level) {
            case CRITICAL -> "URGENT";
            case WARNING -> "HIGH";
            case INFO -> "NORMAL";
            case RECOVERED -> "LOW";
        };
    }

    private String mapToNotificationChannel(AlertLevel level) {
        if (AlertLevel.CRITICAL.equals(level)) {
            return "SMS,APP,SCREEN";
        } else if (AlertLevel.WARNING.equals(level)) {
            return "APP,SCREEN";
        }
        return "APP";
    }

    public void clearAlertCooldown(String deviceId) {
        lastAlertTime.keySet().removeIf(key -> key.startsWith(deviceId + ":"));
        alertCount.keySet().removeIf(key -> key.startsWith(deviceId + ":"));
        log.info("Cleared alert cooldown for device: {}", deviceId);
    }

    public Map<String, Object> getAlertStats(String deviceId) {
        Map<String, Object> stats = new HashMap<>();
        
        int criticalCount = 0;
        int warningCount = 0;
        int infoCount = 0;
        int recoveredCount = 0;
        
        for (Map.Entry<String, Integer> entry : alertCount.entrySet()) {
            if (entry.getKey().startsWith(deviceId + ":")) {
                String level = entry.getKey().split(":")[1];
                int count = entry.getValue();
                
                switch (AlertLevel.valueOf(level)) {
                    case CRITICAL -> criticalCount = count;
                    case WARNING -> warningCount = count;
                    case INFO -> infoCount = count;
                    case RECOVERED -> recoveredCount = count;
                }
            }
        }
        
        stats.put("deviceId", deviceId);
        stats.put("criticalAlerts", criticalCount);
        stats.put("warningAlerts", warningCount);
        stats.put("infoAlerts", infoCount);
        stats.put("recoveredAlerts", recoveredCount);
        stats.put("totalAlerts", criticalCount + warningCount + infoCount + recoveredCount);
        
        return stats;
    }

    public enum AlertLevel {
        INFO,
        WARNING,
        CRITICAL,
        RECOVERED
    }
}
