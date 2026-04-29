package com.parking.transaction;

import com.parking.entity.Payment;
import com.parking.entity.ParkingRecord;
import com.parking.repository.PaymentRepository;
import com.parking.repository.ParkingRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderStatusVerificationService {

    private final PaymentRepository paymentRepository;
    private final ParkingRecordRepository parkingRecordRepository;
    private final TransactionCompensationService compensationService;
    private final IdempotentService idempotentService;

    private static final int VERIFICATION_INTERVAL_MINUTES = 5;
    private static final int GRACE_PERIOD_MINUTES = 15;

    @Scheduled(fixedRate = 60000)
    public void verifyPendingOrders() {
        log.debug("开始校验待处理订单状态...");
        
        LocalDateTime now = LocalDateTime.now();
        
        List<Payment> pendingPayments = paymentRepository.findByStatus("PENDING");
        
        for (Payment payment : pendingPayments) {
            try {
                verifyPaymentOrder(payment, now);
            } catch (Exception e) {
                log.error("校验支付订单失败: paymentNumber={}", payment.getPaymentNumber(), e);
            }
        }
        
        log.debug("订单状态校验完成");
    }

    @Scheduled(fixedRate = 300000)
    public void verifyActiveParkingRecords() {
        log.debug("开始校验活跃停车记录...");
        
        List<ParkingRecord> activeRecords = parkingRecordRepository.findByStatus("ACTIVE");
        
        for (ParkingRecord record : activeRecords) {
            try {
                verifyParkingRecord(record);
            } catch (Exception e) {
                log.error("校验停车记录失败: recordId={}", record.getId(), e);
            }
        }
        
        log.debug("活跃停车记录校验完成");
    }

    @Transactional
    public void verifyPaymentOrder(Payment payment, LocalDateTime now) {
        String paymentNumber = payment.getPaymentNumber();
        
        if (payment.getExpiredTime() == null) {
            return;
        }
        
        if (now.isAfter(payment.getExpiredTime())) {
            log.warn("支付订单已过期: paymentNumber={}", paymentNumber);
            
            Optional<ParkingRecord> recordOpt = Optional.ofNullable(payment.getParkingRecord());
            
            if (recordOpt.isPresent()) {
                ParkingRecord record = recordOpt.get();
                
                if ("PAID".equals(record.getStatus()) || "COMPLETED".equals(record.getStatus())) {
                    log.info("停车记录已支付，标记支付订单为已支付: paymentNumber={}", paymentNumber);
                    payment.setStatus("PAID");
                    payment.setPaidTime(now);
                    paymentRepository.save(payment);
                    return;
                }
            }
            
            payment.setStatus("EXPIRED");
            paymentRepository.save(payment);
            
            log.info("支付订单已标记为过期: paymentNumber={}", paymentNumber);
        }
        
        if (payment.getPaidTime() != null) {
            verifyPaymentConsistency(payment);
        }
    }

    @Transactional
    public void verifyPaymentConsistency(Payment payment) {
        String paymentNumber = payment.getPaymentNumber();
        
        if (!"PAID".equals(payment.getStatus())) {
            return;
        }
        
        Optional<ParkingRecord> recordOpt = Optional.ofNullable(payment.getParkingRecord());
        
        if (recordOpt.isEmpty()) {
            log.warn("支付订单没有关联停车记录: paymentNumber={}", paymentNumber);
            return;
        }
        
        ParkingRecord record = recordOpt.get();
        
        if (!"PAID".equals(record.getStatus()) && !"COMPLETED".equals(record.getStatus())) {
            log.warn("检测到支付状态不一致: paymentNumber={}, recordStatus={}", 
                     paymentNumber, record.getStatus());
            
            record.setStatus("PAID");
            record.setPaymentMethod(payment.getPaymentMethod());
            record.setPaymentTransactionId(payment.getTransactionId());
            record.setPaymentTime(payment.getPaidTime());
            record.setAmount(payment.getPaidAmount());
            parkingRecordRepository.save(record);
            
            log.info("已修复支付状态不一致: paymentNumber={}", paymentNumber);
        }
    }

    @Transactional
    public void verifyParkingRecord(ParkingRecord record) {
        String plateNumber = record.getPlateNumber();
        Long recordId = record.getId();
        
        if (record.getExitTime() != null && !"COMPLETED".equals(record.getStatus())) {
            log.warn("检测到出场但状态未完成: recordId={}, plateNumber={}", recordId, plateNumber);
            
            if (record.getAmount() != null && record.getAmount().doubleValue() > 0) {
                if (record.getPaymentTransactionId() != null) {
                    record.setStatus("COMPLETED");
                    parkingRecordRepository.save(record);
                    log.info("已修复停车记录状态: recordId={}", recordId);
                } else {
                    log.warn("停车记录有出场时间但没有支付记录: recordId={}", recordId);
                }
            } else {
                record.setStatus("COMPLETED");
                parkingRecordRepository.save(record);
                log.info("免费停车记录已标记为完成: recordId={}", recordId);
            }
        }
        
        if (record.getEntryTime() != null && "ACTIVE".equals(record.getStatus())) {
            LocalDateTime entryTime = record.getEntryTime();
            LocalDateTime now = LocalDateTime.now();
            
            long hours = java.time.Duration.between(entryTime, now).toHours();
            
            if (hours > 24) {
                log.warn("检测到长时间未出场的停车记录: recordId={}, plateNumber={}, hours={}", 
                         recordId, plateNumber, hours);
            }
        }
    }

    @Transactional
    public VerificationResult verifyOrderStatus(String paymentNumber) {
        log.info("人工校验订单状态: paymentNumber={}", paymentNumber);
        
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentNumber(paymentNumber);
        
        if (paymentOpt.isEmpty()) {
            return VerificationResult.builder()
                .success(false)
                .message("支付订单不存在")
                .build();
        }
        
        Payment payment = paymentOpt.get();
        
        VerificationResult.VerificationResultBuilder builder = VerificationResult.builder()
            .paymentNumber(paymentNumber)
            .paymentStatus(payment.getStatus())
            .paidAmount(payment.getPaidAmount())
            .paidTime(payment.getPaidTime())
            .transactionId(payment.getTransactionId());
        
        boolean consistent = true;
        StringBuilder issues = new StringBuilder();
        
        if (payment.getParkingRecord() != null) {
            ParkingRecord record = payment.getParkingRecord();
            builder.parkingRecordId(record.getId())
                .parkingRecordStatus(record.getStatus())
                .recordAmount(record.getAmount());
            
            if ("PAID".equals(payment.getStatus())) {
                if (!"PAID".equals(record.getStatus()) && !"COMPLETED".equals(record.getStatus())) {
                    consistent = false;
                    issues.append("支付已成功但停车记录状态未更新; ");
                    
                    record.setStatus("PAID");
                    record.setPaymentMethod(payment.getPaymentMethod());
                    record.setPaymentTransactionId(payment.getTransactionId());
                    record.setPaymentTime(payment.getPaidTime());
                    parkingRecordRepository.save(record);
                    
                    issues.append("已自动修复; ");
                }
            }
        } else {
            consistent = false;
            issues.append("支付订单未关联停车记录; ");
        }
        
        if ("PAID".equals(payment.getStatus()) && payment.getPaidTime() == null) {
            consistent = false;
            issues.append("支付状态为PAID但没有支付时间; ");
        }
        
        if ("PAID".equals(payment.getStatus()) && payment.getTransactionId() == null) {
            consistent = false;
            issues.append("支付状态为PAID但没有交易ID; ");
        }
        
        builder.consistent(consistent)
            .success(true)
            .message(consistent ? "订单状态一致" : "检测到状态不一致: " + issues.toString());
        
        return builder.build();
    }

    @Transactional
    public void resolveDuplicatePayment(String plateNumber) {
        log.info("检查并解决重复支付: plateNumber={}", plateNumber);
        
        List<Payment> payments = paymentRepository.findByPlateNumber(plateNumber);
        
        if (payments.size() <= 1) {
            log.info("没有重复支付记录: plateNumber={}", plateNumber);
            return;
        }
        
        long paidCount = payments.stream()
            .filter(p -> "PAID".equals(p.getStatus()))
            .count();
        
        if (paidCount > 1) {
            log.warn("检测到多笔成功支付: plateNumber={}, paidCount={}", plateNumber, paidCount);
            
            List<Payment> paidPayments = payments.stream()
                .filter(p -> "PAID".equals(p.getStatus()))
                .sorted((a, b) -> b.getPaidTime().compareTo(a.getPaidTime()))
                .toList();
            
            Payment latestPayment = paidPayments.get(0);
            
            for (int i = 1; i < paidPayments.size(); i++) {
                Payment duplicate = paidPayments.get(i);
                log.info("标记重复支付为待退款: paymentNumber={}", duplicate.getPaymentNumber());
                duplicate.setStatus("TO_BE_REFUNDED");
                paymentRepository.save(duplicate);
            }
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class VerificationResult {
        private boolean success;
        private boolean consistent;
        private String message;
        private String paymentNumber;
        private String paymentStatus;
        private java.math.BigDecimal paidAmount;
        private LocalDateTime paidTime;
        private String transactionId;
        private Long parkingRecordId;
        private String parkingRecordStatus;
        private java.math.BigDecimal recordAmount;
    }
}
