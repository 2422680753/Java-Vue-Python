package com.parking.service;

import com.parking.entity.Notification;
import com.parking.entity.ParkingLot;
import com.parking.repository.NotificationRepository;
import com.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final ParkingService parkingService;
    private final PredictionService predictionService;
    private final WebSocketService webSocketService;

    @Scheduled(cron = "0 */10 * * * ?")
    public void checkAndSendPeakWarnings() {
        log.info("检查高峰期预警");

        List<ParkingLot> activeLots = parkingLotRepository.findByStatus("ACTIVE");

        for (ParkingLot lot : activeLots) {
            checkPeakWarningForLot(lot);
        }
    }

    private void checkPeakWarningForLot(ParkingLot lot) {
        LocalDateTime now = LocalDateTime.now();

        boolean isCurrentlyPeak = predictionService.isPeakHour(now);
        boolean willBePeakSoon = predictionService.isPeakHour(now.plusMinutes(30));

        Long availableSpots = parkingService.countAvailableSpots(lot.getId());
        double occupancyRate = (double) (lot.getTotalSpots() - availableSpots) / lot.getTotalSpots();

        if (isCurrentlyPeak) {
            if (occupancyRate > 0.9) {
                sendPeakWarning(lot, "高峰期车位紧张，当前使用率已达90%！", availableSpots.intValue());
            } else if (occupancyRate > 0.8) {
                sendPeakWarning(lot, "高峰期已到，请留意车位使用情况", availableSpots.intValue());
            }
        } else if (willBePeakSoon) {
            if (occupancyRate > 0.7) {
                sendPeakWarning(lot, "即将进入高峰期，请提前做好准备", availableSpots.intValue());
            }
        }
    }

    private void sendPeakWarning(ParkingLot lot, String message, int availableSpots) {
        Notification notification = new Notification();
        notification.setTitle("高峰期预警");
        notification.setContent(message);
        notification.setType("PEAK_WARNING");
        notification.setPriority("HIGH");
        notification.setStatus("PENDING");
        notification.setChannel("SCREEN");

        notificationRepository.save(notification);

        webSocketService.sendPeakWarning(lot.getId(), message, availableSpots);

        log.warn("高峰期预警: 停车场={}, 消息={}, 可用车位={}", lot.getName(), message, availableSpots);
    }

    public void sendPaymentReminder(Long parkingRecordId, String plateNumber, double amount) {
        Notification notification = new Notification();
        notification.setTitle("停车支付提醒");
        notification.setContent("您的车辆 " + plateNumber + " 停车费用为 " + amount + " 元，请及时支付");
        notification.setType("PAYMENT_REMINDER");
        notification.setPriority("NORMAL");
        notification.setPlateNumber(plateNumber);
        notification.setStatus("PENDING");
        notification.setChannel("APP");

        notificationRepository.save(notification);

        log.info("发送支付提醒: 车牌={}, 金额={}", plateNumber, amount);
    }

    public void sendPaymentSuccess(String plateNumber, double amount, String paymentMethod) {
        Notification notification = new Notification();
        notification.setTitle("支付成功");
        notification.setContent("您的车辆 " + plateNumber + " 已成功支付 " + amount + " 元，支付方式: " + paymentMethod);
        notification.setType("PAYMENT_SUCCESS");
        notification.setPriority("NORMAL");
        notification.setPlateNumber(plateNumber);
        notification.setStatus("SENT");
        notification.setSentTime(LocalDateTime.now());
        notification.setChannel("APP");

        notificationRepository.save(notification);

        log.info("支付成功通知: 车牌={}, 金额={}", plateNumber, amount);
    }

    public void sendSpotAvailablePrediction(String spotNumber, String plateNumber, LocalDateTime predictedTime) {
        Notification notification = new Notification();
        notification.setTitle("车位即将可用");
        notification.setContent("车位 " + spotNumber + " 预计于 " + predictedTime + " 可用");
        notification.setType("SPOT_AVAILABLE");
        notification.setPriority("NORMAL");
        notification.setStatus("PENDING");
        notification.setChannel("SCREEN");

        notificationRepository.save(notification);

        log.info("车位可用预测通知: 车位={}, 预计时间={}", spotNumber, predictedTime);
    }

    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));

        notification.setStatus("READ");
        notification.setReadTime(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    public List<Notification> getRecentNotifications(int limit) {
        return notificationRepository.findTopByOrderByCreatedAtDesc(limit);
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByTargetUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
    }
}