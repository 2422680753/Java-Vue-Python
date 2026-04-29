package com.parking.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.entity.Payment;
import com.parking.entity.ParkingRecord;
import com.parking.entity.ParkingSpot;
import com.parking.repository.PaymentRepository;
import com.parking.repository.ParkingRecordRepository;
import com.parking.repository.ParkingSpotRepository;
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
public class TransactionCompensationService {

    private final TransactionLogRepository transactionLogRepository;
    private final IdempotentService idempotentService;
    private final PaymentRepository paymentRepository;
    private final ParkingRecordRepository parkingRecordRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<TransactionState> RETRYABLE_STATES = Arrays.asList(
        TransactionState.PENDING,
        TransactionState.FAILED,
        TransactionState.TIMEOUT,
        TransactionState.RETRYING
    );

    private static final List<TransactionState> COMPENSABLE_STATES = Arrays.asList(
        TransactionState.PROCESSING,
        TransactionState.TO_BE_CANCELLED,
        TransactionState.FAILED,
        TransactionState.TIMEOUT
    );

    @Scheduled(fixedRate = 30000)
    public void processRetryQueue() {
        log.debug("开始处理重试队列...");
        
        LocalDateTime now = LocalDateTime.now();
        
        List<TransactionLog> pendingRetry = transactionLogRepository.findPendingRetry(
            TransactionState.RETRYING, now
        );
        
        for (TransactionLog logEntry : pendingRetry) {
            try {
                retryTransaction(logEntry);
            } catch (Exception e) {
                log.error("重试事务失败: transactionId={}", logEntry.getTransactionId(), e);
            }
        }
        
        log.debug("重试队列处理完成");
    }

    @Scheduled(fixedRate = 60000)
    public void checkTimeoutTransactions() {
        log.debug("开始检查超时事务...");
        
        LocalDateTime now = LocalDateTime.now();
        
        List<TransactionLog> timeoutTransactions = transactionLogRepository.findTimeoutTransactions(
            Arrays.asList(TransactionState.PROCESSING, TransactionState.PENDING),
            now
        );
        
        for (TransactionLog logEntry : timeoutTransactions) {
            try {
                handleTimeoutTransaction(logEntry);
            } catch (Exception e) {
                log.error("处理超时事务失败: transactionId={}", logEntry.getTransactionId(), e);
            }
        }
        
        log.debug("超时事务检查完成");
    }

    @Scheduled(fixedRate = 120000)
    public void checkCompensationNeeded() {
        log.debug("开始检查需要补偿的事务...");
        
        List<TransactionLog> toBeCancelled = transactionLogRepository.findByState(
            TransactionState.TO_BE_CANCELLED
        );
        
        for (TransactionLog logEntry : toBeCancelled) {
            try {
                compensateTransaction(logEntry);
            } catch (Exception e) {
                log.error("补偿事务失败: transactionId={}", logEntry.getTransactionId(), e);
            }
        }
        
        log.debug("补偿检查完成");
    }

    @Transactional
    public void retryTransaction(TransactionLog logEntry) {
        log.info("重试事务: transactionId={}, type={}, retryCount={}", 
                 logEntry.getTransactionId(), logEntry.getTransactionType(), logEntry.getRetryCount());
        
        if (logEntry.getRetryCount() >= logEntry.getMaxRetryCount()) {
            log.warn("事务重试次数已达上限: transactionId={}", logEntry.getTransactionId());
            markAsFailed(logEntry, "重试次数已达上限");
            return;
        }
        
        try {
            switch (logEntry.getTransactionType()) {
                case PAYMENT_PROCESS:
                    retryPaymentProcess(logEntry);
                    break;
                case PAYMENT_CREATE:
                    retryPaymentCreate(logEntry);
                    break;
                case VEHICLE_ENTRY:
                    retryVehicleEntry(logEntry);
                    break;
                case VEHICLE_EXIT:
                    retryVehicleExit(logEntry);
                    break;
                default:
                    log.warn("不支持的事务类型: {}", logEntry.getTransactionType());
                    markAsFailed(logEntry, "不支持的事务类型");
            }
        } catch (Exception e) {
            log.error("重试事务失败: transactionId={}", logEntry.getTransactionId(), e);
            idempotentService.markTransactionForRetry(logEntry.getTransactionId(), e.getMessage());
        }
    }

    private void retryPaymentProcess(TransactionLog logEntry) {
        String paymentNumber = logEntry.getPaymentNumber();
        
        if (paymentNumber == null) {
            markAsFailed(logEntry, "缺少支付单号");
            return;
        }
        
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentNumber(paymentNumber);
        
        if (paymentOpt.isEmpty()) {
            markAsFailed(logEntry, "支付记录不存在");
            return;
        }
        
        Payment payment = paymentOpt.get();
        
        if ("PAID".equals(payment.getStatus())) {
            idempotentService.updateTransactionState(
                logEntry.getTransactionId(),
                TransactionState.SUCCEEDED,
                "支付已成功",
                null
            );
            log.info("支付已成功，无需重试: paymentNumber={}", paymentNumber);
            return;
        }
        
        if ("EXPIRED".equals(payment.getStatus())) {
            markAsFailed(logEntry, "支付订单已过期");
            return;
        }
        
        log.info("继续等待支付: paymentNumber={}", paymentNumber);
        idempotentService.markTransactionForRetry(logEntry.getTransactionId(), "等待支付确认");
    }

    private void retryPaymentCreate(TransactionLog logEntry) {
        String businessKey = logEntry.getBusinessKey();
        
        if (businessKey == null) {
            markAsFailed(logEntry, "缺少业务键");
            return;
        }
        
        Optional<Payment> existingPayment = paymentRepository.findByPaymentNumber(businessKey);
        
        if (existingPayment.isPresent()) {
            idempotentService.updateTransactionState(
                logEntry.getTransactionId(),
                TransactionState.SUCCEEDED,
                "支付订单已创建",
                null
            );
            return;
        }
        
        markAsFailed(logEntry, "无法创建支付订单，请重新发起");
    }

    private void retryVehicleEntry(TransactionLog logEntry) {
        String plateNumber = logEntry.getPlateNumber();
        
        if (plateNumber == null) {
            markAsFailed(logEntry, "缺少车牌号");
            return;
        }
        
        Optional<ParkingRecord> activeRecord = parkingRecordRepository.findActiveByPlateNumber(plateNumber);
        
        if (activeRecord.isPresent()) {
            idempotentService.updateTransactionState(
                logEntry.getTransactionId(),
                TransactionState.SUCCEEDED,
                "车辆已入场",
                null
            );
            log.info("车辆已入场: plateNumber={}", plateNumber);
            return;
        }
        
        markAsFailed(logEntry, "无法完成入场，请重新识别");
    }

    private void retryVehicleExit(TransactionLog logEntry) {
        String plateNumber = logEntry.getPlateNumber();
        
        if (plateNumber == null) {
            markAsFailed(logEntry, "缺少车牌号");
            return;
        }
        
        Optional<ParkingRecord> activeRecord = parkingRecordRepository.findActiveByPlateNumber(plateNumber);
        
        if (activeRecord.isEmpty()) {
            idempotentService.updateTransactionState(
                logEntry.getTransactionId(),
                TransactionState.SUCCEEDED,
                "车辆已出场",
                null
            );
            log.info("车辆已出场: plateNumber={}", plateNumber);
            return;
        }
        
        markAsFailed(logEntry, "无法完成出场，请重新识别");
    }

    @Transactional
    public void compensateTransaction(TransactionLog logEntry) {
        log.info("开始补偿事务: transactionId={}, type={}", 
                 logEntry.getTransactionId(), logEntry.getTransactionType());
        
        idempotentService.updateTransactionState(
            logEntry.getTransactionId(),
            TransactionState.COMPENSATING,
            null,
            "开始补偿"
        );
        
        try {
            switch (logEntry.getTransactionType()) {
                case PAYMENT_PROCESS:
                    compensatePaymentProcess(logEntry);
                    break;
                case PAYMENT_CREATE:
                    compensatePaymentCreate(logEntry);
                    break;
                case VEHICLE_ENTRY:
                    compensateVehicleEntry(logEntry);
                    break;
                default:
                    log.warn("不支持的补偿类型: {}", logEntry.getTransactionType());
            }
            
            idempotentService.updateTransactionState(
                logEntry.getTransactionId(),
                TransactionState.COMPENSATED,
                "补偿完成",
                null
            );
            
            log.info("事务补偿完成: transactionId={}", logEntry.getTransactionId());
            
        } catch (Exception e) {
            log.error("事务补偿失败: transactionId={}", logEntry.getTransactionId(), e);
            idempotentService.updateTransactionState(
                logEntry.getTransactionId(),
                TransactionState.FAILED,
                null,
                "补偿失败: " + e.getMessage()
            );
        }
    }

    private void compensatePaymentProcess(TransactionLog logEntry) {
        String paymentNumber = logEntry.getPaymentNumber();
        
        if (paymentNumber == null) {
            return;
        }
        
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentNumber(paymentNumber);
        
        if (paymentOpt.isEmpty()) {
            return;
        }
        
        Payment payment = paymentOpt.get();
        
        if ("PAID".equals(payment.getStatus())) {
            log.warn("支付已成功，无法补偿: paymentNumber={}", paymentNumber);
            return;
        }
        
        payment.setStatus("CANCELLED");
        paymentRepository.save(payment);
        
        log.info("取消支付订单: paymentNumber={}", paymentNumber);
    }

    private void compensatePaymentCreate(TransactionLog logEntry) {
        String paymentNumber = logEntry.getPaymentNumber();
        
        if (paymentNumber == null) {
            return;
        }
        
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentNumber(paymentNumber);
        
        if (paymentOpt.isEmpty()) {
            return;
        }
        
        Payment payment = paymentOpt.get();
        
        if (!"PENDING".equals(payment.getStatus())) {
            return;
        }
        
        payment.setStatus("CANCELLED");
        paymentRepository.save(payment);
        
        log.info("取消创建的支付订单: paymentNumber={}", paymentNumber);
    }

    private void compensateVehicleEntry(TransactionLog logEntry) {
        String plateNumber = logEntry.getPlateNumber();
        
        if (plateNumber == null) {
            return;
        }
        
        Optional<ParkingRecord> activeRecord = parkingRecordRepository.findActiveByPlateNumber(plateNumber);
        
        if (activeRecord.isEmpty()) {
            return;
        }
        
        ParkingRecord record = activeRecord.get();
        record.setStatus("CANCELLED");
        parkingRecordRepository.save(record);
        
        if (record.getParkingSpot() != null) {
            ParkingSpot spot = record.getParkingSpot();
            spot.setStatus("AVAILABLE");
            spot.setCurrentPlateNumber(null);
            spot.setOccupiedSince(null);
            parkingSpotRepository.save(spot);
        }
        
        log.info("补偿车辆入场: plateNumber={}", plateNumber);
    }

    @Transactional
    public void handleTimeoutTransaction(TransactionLog logEntry) {
        log.warn("处理超时事务: transactionId={}, type={}, state={}", 
                 logEntry.getTransactionId(), logEntry.getTransactionType(), logEntry.getState());
        
        idempotentService.updateTransactionState(
            logEntry.getTransactionId(),
            TransactionState.TIMEOUT,
            null,
            "事务超时"
        );
        
        if (logEntry.getTransactionType().requiresCompensation()) {
            logEntry.setState(TransactionState.TO_BE_CANCELLED);
            transactionLogRepository.save(logEntry);
            log.info("标记为待补偿: transactionId={}", logEntry.getTransactionId());
        }
    }

    private void markAsFailed(TransactionLog logEntry, String reason) {
        idempotentService.updateTransactionState(
            logEntry.getTransactionId(),
            TransactionState.FAILED,
            null,
            reason
        );
        log.warn("事务失败: transactionId={}, reason={}", logEntry.getTransactionId(), reason);
    }

    @Transactional
    public TransactionLog startTransaction(TransactionType type, String businessKey,
                                            String plateNumber, String paymentNumber,
                                            String requestData) {
        if (idempotentService.checkAndMarkDuplicate(businessKey, type)) {
            throw new IllegalStateException("重复请求，已处理或处理中");
        }
        
        return idempotentService.createTransaction(type, businessKey, plateNumber, 
                                                     paymentNumber, requestData, MAX_RETRY_COUNT);
    }
}
