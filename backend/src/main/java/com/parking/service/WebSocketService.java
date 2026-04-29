package com.parking.service;

import com.parking.dto.ParkingLotStatus;
import com.parking.dto.PredictionResult;
import com.parking.dto.SpotStatusUpdate;
import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingSpot;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void sendSpotUpdate(Long parkingLotId, ParkingSpot spot, String newStatus) {
        try {
            SpotStatusUpdate update = new SpotStatusUpdate();
            update.setSpotNumber(spot.getSpotNumber());
            update.setStatus(newStatus);
            update.setZone(spot.getZone());
            update.setFloor(spot.getFloor());
            update.setPlateNumber(spot.getCurrentPlateNumber());
            update.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/spots",
                    update
            );

            log.debug("发送车位状态更新: 停车场={}, 车位={}, 状态={}", parkingLotId, spot.getSpotNumber(), newStatus);

        } catch (Exception e) {
            log.error("发送WebSocket消息失败: {}", e.getMessage(), e);
        }
    }

    public void sendParkingLotUpdate(Long parkingLotId) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("parkingLotId", parkingLotId);
            update.put("timestamp", System.currentTimeMillis());
            update.put("eventType", "PARKING_LOT_STATUS_UPDATED");

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/status",
                    update
            );

            log.debug("发送停车场状态更新: 停车场={}", parkingLotId);

        } catch (Exception e) {
            log.error("发送停车场状态更新失败: {}", e.getMessage(), e);
        }
    }

    public void sendParkingLotStatus(ParkingLotStatus status) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + status.getParkingLotId() + "/dashboard",
                    status
            );

            log.debug("发送停车场大屏状态: 停车场={}", status.getParkingLotId());

        } catch (Exception e) {
            log.error("发送大屏状态失败: {}", e.getMessage(), e);
        }
    }

    public void sendPredictionUpdate(Long parkingLotId, List<PredictionResult> predictions) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("parkingLotId", parkingLotId);
            update.put("predictions", predictions);
            update.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/predictions",
                    update
            );

            log.debug("发送预测更新: 停车场={}, 预测数量={}", parkingLotId, predictions.size());

        } catch (Exception e) {
            log.error("发送预测更新失败: {}", e.getMessage(), e);
        }
    }

    public void sendEntryNotification(Long parkingLotId, String plateNumber, String spotNumber) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "ENTRY");
            notification.put("plateNumber", plateNumber);
            notification.put("spotNumber", spotNumber);
            notification.put("parkingLotId", parkingLotId);
            notification.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/events",
                    notification
            );

            log.info("发送入场通知: 车牌={}, 车位={}", plateNumber, spotNumber);

        } catch (Exception e) {
            log.error("发送入场通知失败: {}", e.getMessage(), e);
        }
    }

    public void sendExitNotification(Long parkingLotId, String plateNumber, long durationMinutes, double amount) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "EXIT");
            notification.put("plateNumber", plateNumber);
            notification.put("durationMinutes", durationMinutes);
            notification.put("amount", amount);
            notification.put("parkingLotId", parkingLotId);
            notification.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/events",
                    notification
            );

            log.info("发送出场通知: 车牌={}, 时长={}分钟, 金额={}", plateNumber, durationMinutes, amount);

        } catch (Exception e) {
            log.error("发送出场通知失败: {}", e.getMessage(), e);
        }
    }

    public void sendPeakWarning(Long parkingLotId, String message, int availableSpots) {
        try {
            Map<String, Object> warning = new HashMap<>();
            warning.put("type", "PEAK_WARNING");
            warning.put("message", message);
            warning.put("availableSpots", availableSpots);
            warning.put("parkingLotId", parkingLotId);
            warning.put("timestamp", System.currentTimeMillis());
            warning.put("priority", "HIGH");

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/warnings",
                    warning
            );

            messagingTemplate.convertAndSend(
                    "/topic/all/warnings",
                    warning
            );

            log.warn("发送高峰期预警: 停车场={}, 可用车位={}", parkingLotId, availableSpots);

        } catch (Exception e) {
            log.error("发送高峰期预警失败: {}", e.getMessage(), e);
        }
    }

    public void sendPaymentNotification(Long parkingLotId, String plateNumber, double amount, String paymentMethod) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "PAYMENT");
            notification.put("plateNumber", plateNumber);
            notification.put("amount", amount);
            notification.put("paymentMethod", paymentMethod);
            notification.put("parkingLotId", parkingLotId);
            notification.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(
                    "/topic/parking-lot/" + parkingLotId + "/payments",
                    notification
            );

            log.info("发送支付通知: 车牌={}, 金额={}, 方式={}", plateNumber, amount, paymentMethod);

        } catch (Exception e) {
            log.error("发送支付通知失败: {}", e.getMessage(), e);
        }
    }
}