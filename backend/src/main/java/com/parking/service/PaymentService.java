package com.parking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.dto.PaymentRequest;
import com.parking.dto.PaymentResponse;
import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingRecord;
import com.parking.entity.Payment;
import com.parking.repository.ParkingLotRepository;
import com.parking.repository.ParkingRecordRepository;
import com.parking.repository.PaymentRepository;
import com.parking.transaction.IdempotentService;
import com.parking.transaction.TransactionCompensationService;
import com.parking.transaction.TransactionState;
import com.parking.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ParkingRecordRepository parkingRecordRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final IdempotentService idempotentService;
    private final TransactionCompensationService compensationService;
    private final ObjectMapper objectMapper;

    @Value("${payment.qr-code.base-url:http://localhost:8080/api/payment/pay/}")
    private String qrCodeBaseUrl;

    private final ConcurrentHashMap<String, Payment> pendingPayments = new ConcurrentHashMap<>();

    public BigDecimal calculateParkingFee(ParkingRecord record) {
        if (record.getParkingLot() == null) {
            return BigDecimal.ZERO;
        }

        ParkingLot lot = record.getParkingLot();
        long minutes = Optional.ofNullable(record.getDurationMinutes()).orElse(0L);

        if (minutes <= 30) {
            return BigDecimal.ZERO;
        }

        double hourlyRate = lot.getHourlyRate();
        double dailyMaxRate = lot.getDailyMaxRate();

        long hours = (minutes + 59) / 60;
        double total = hours * hourlyRate;

        total = Math.min(total, dailyMaxRate);

        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public PaymentResponse createPaymentOrder(Long parkingRecordId, PaymentRequest request) {
        ParkingRecord record = parkingRecordRepository.findById(parkingRecordId)
                .orElseThrow(() -> new IllegalArgumentException("停车记录不存在"));

        if ("COMPLETED".equals(record.getStatus())) {
            throw new IllegalStateException("该订单已完成支付");
        }

        BigDecimal amount = calculateParkingFee(record);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            record.setStatus("COMPLETED");
            record.setAmount(BigDecimal.ZERO);
            parkingRecordRepository.save(record);

            PaymentResponse response = new PaymentResponse();
            response.setSuccess(true);
            response.setMessage("停车时长不足30分钟，免费放行");
            response.setAmount(BigDecimal.ZERO);
            response.setPaid(true);
            return response;
        }

        String idempotentKey = idempotentService.generateIdempotentKey(
            "payment:create", 
            record.getPlateNumber(), 
            parkingRecordId
        );

        if (idempotentService.isRequestAlreadyProcessed(idempotentKey)) {
            log.info("检测到重复创建支付订单请求: idempotentKey={}", idempotentKey);
            
            Optional<Payment> existingPayment = paymentRepository.findByPlateNumber(record.getPlateNumber())
                    .stream()
                    .filter(p -> "PENDING".equals(p.getStatus()))
                    .findFirst();
            
            if (existingPayment.isPresent()) {
                Payment payment = existingPayment.get();
                PaymentResponse response = new PaymentResponse();
                response.setSuccess(true);
                response.setPaymentNumber(payment.getPaymentNumber());
                response.setQrCodeUrl(payment.getQrCodeUrl());
                response.setAmount(payment.getAmount());
                response.setMessage("已有待支付订单（幂等处理）");
                return response;
            }
        }

        synchronized (pendingPayments) {
            String plateNumber = record.getPlateNumber();
            if (pendingPayments.containsKey(plateNumber)) {
                Payment existing = pendingPayments.get(plateNumber);
                if ("PENDING".equals(existing.getStatus())) {
                    PaymentResponse response = new PaymentResponse();
                    response.setSuccess(true);
                    response.setPaymentNumber(existing.getPaymentNumber());
                    response.setQrCodeUrl(existing.getQrCodeUrl());
                    response.setAmount(existing.getAmount());
                    response.setMessage("已有待支付订单");
                    return response;
                }
            }
        }

        String requestData = null;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("parkingRecordId", parkingRecordId);
            data.put("plateNumber", record.getPlateNumber());
            data.put("amount", amount);
            data.put("paymentMethod", request.getPaymentMethod());
            requestData = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("无法序列化请求数据: {}", e.getMessage());
        }

        String paymentNumber = "PY" + System.currentTimeMillis();

        var transactionLog = compensationService.startTransaction(
            TransactionType.PAYMENT_CREATE,
            idempotentKey,
            record.getPlateNumber(),
            paymentNumber,
            requestData
        );

        Payment payment = new Payment();
        payment.setPaymentNumber(paymentNumber);
        payment.setPlateNumber(record.getPlateNumber());
        payment.setAmount(amount);
        payment.setPaidAmount(amount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus("PENDING");
        payment.setPaymentProvider("SIMULATED");
        payment.setExpiredTime(LocalDateTime.now().plusMinutes(15));
        payment.setParkingRecord(record);
        payment.setQrCodeUrl(generateQrCodeUrl(paymentNumber, amount));

        Payment savedPayment = paymentRepository.save(payment);
        pendingPayments.put(record.getPlateNumber(), savedPayment);

        record.setAmount(amount);
        record.setStatus("PAID");
        parkingRecordRepository.save(record);

        idempotentService.updateTransactionState(
            transactionLog.getTransactionId(),
            TransactionState.SUCCEEDED,
            "支付订单创建成功",
            null
        );

        idempotentService.storeIdempotentKey(idempotentKey);

        PaymentResponse response = new PaymentResponse();
        response.setSuccess(true);
        response.setPaymentNumber(paymentNumber);
        response.setQrCodeUrl(savedPayment.getQrCodeUrl());
        response.setAmount(amount);
        response.setExpiredTime(savedPayment.getExpiredTime());
        response.setMessage("支付订单创建成功，请在15分钟内完成支付");

        log.info("创建支付订单: {}, 车牌: {}, 金额: {}, 事务ID: {}", 
                 paymentNumber, record.getPlateNumber(), amount, transactionLog.getTransactionId());
        return response;
    }

    private String generateQrCodeUrl(String paymentNumber, BigDecimal amount) {
        return qrCodeBaseUrl + paymentNumber + "?amount=" + amount;
    }

    @Transactional
    public PaymentResponse processPayment(String paymentNumber) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));

        String idempotentKey = idempotentService.generateIdempotentKey(
            "payment:process", 
            payment.getPlateNumber(), 
            paymentNumber
        );

        if (idempotentService.isRequestAlreadyProcessed(idempotentKey)) {
            log.info("检测到重复支付请求: idempotentKey={}", idempotentKey);
            
            if ("PAID".equals(payment.getStatus())) {
                PaymentResponse response = new PaymentResponse();
                response.setSuccess(true);
                response.setPaymentNumber(paymentNumber);
                response.setAmount(payment.getAmount());
                response.setPaid(true);
                response.setPaidTime(payment.getPaidTime());
                response.setTransactionId(payment.getTransactionId());
                response.setMessage("支付已成功（幂等处理）");
                return response;
            }
        }

        if (!"PENDING".equals(payment.getStatus())) {
            PaymentResponse response = new PaymentResponse();
            response.setSuccess(true);
            response.setPaymentNumber(paymentNumber);
            response.setAmount(payment.getAmount());
            response.setPaid("PAID".equals(payment.getStatus()));
            response.setMessage("PAID".equals(payment.getStatus()) ? "订单已支付" : "订单已过期或取消");
            return response;
        }

        if (payment.getExpiredTime() != null && LocalDateTime.now().isAfter(payment.getExpiredTime())) {
            payment.setStatus("EXPIRED");
            paymentRepository.save(payment);
            pendingPayments.remove(payment.getPlateNumber());

            throw new IllegalStateException("支付订单已过期，请重新生成");
        }

        String requestData = null;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("paymentNumber", paymentNumber);
            data.put("plateNumber", payment.getPlateNumber());
            data.put("amount", payment.getAmount());
            requestData = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("无法序列化请求数据: {}", e.getMessage());
        }

        var transactionLog = compensationService.startTransaction(
            TransactionType.PAYMENT_PROCESS,
            idempotentKey,
            payment.getPlateNumber(),
            paymentNumber,
            requestData
        );

        try {
            idempotentService.updateTransactionState(
                transactionLog.getTransactionId(),
                TransactionState.PROCESSING,
                null,
                "开始处理支付"
            );

            payment.setStatus("PAID");
            payment.setPaidTime(LocalDateTime.now());
            payment.setTransactionId(UUID.randomUUID().toString().replace("-", ""));

            Payment savedPayment = paymentRepository.save(payment);

            ParkingRecord record = savedPayment.getParkingRecord();
            if (record != null) {
                record.setPaymentMethod(savedPayment.getPaymentMethod());
                record.setPaymentTransactionId(savedPayment.getTransactionId());
                record.setPaymentTime(LocalDateTime.now());
                record.setStatus("COMPLETED");
                parkingRecordRepository.save(record);
            }

            pendingPayments.remove(payment.getPlateNumber());

            idempotentService.updateTransactionState(
                transactionLog.getTransactionId(),
                TransactionState.SUCCEEDED,
                "支付成功",
                null
            );

            idempotentService.storeIdempotentKey(idempotentKey);

            PaymentResponse response = new PaymentResponse();
            response.setSuccess(true);
            response.setPaymentNumber(paymentNumber);
            response.setAmount(savedPayment.getAmount());
            response.setPaid(true);
            response.setPaidTime(savedPayment.getPaidTime());
            response.setTransactionId(savedPayment.getTransactionId());
            response.setMessage("支付成功，请在15分钟内离场");

            log.info("支付成功: {}, 车牌: {}, 金额: {}, 事务ID: {}", 
                     paymentNumber, payment.getPlateNumber(), payment.getAmount(), 
                     transactionLog.getTransactionId());
            return response;

        } catch (Exception e) {
            log.error("支付处理失败: paymentNumber={}, 错误={}", paymentNumber, e.getMessage(), e);
            
            idempotentService.updateTransactionState(
                transactionLog.getTransactionId(),
                TransactionState.FAILED,
                null,
                "支付失败: " + e.getMessage()
            );

            throw e;
        }
    }

    @Transactional
    public PaymentResponse simulatePaymentWithTransaction(String paymentNumber, String paymentMethod) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));

        String idempotentKey = idempotentService.generateIdempotentKey(
            "payment:simulate", 
            payment.getPlateNumber(), 
            paymentNumber
        );

        if (idempotentService.isRequestAlreadyProcessed(idempotentKey)) {
            log.info("检测到重复模拟支付请求: idempotentKey={}", idempotentKey);
            return queryPaymentStatus(paymentNumber);
        }

        if ("PAID".equals(payment.getStatus())) {
            idempotentService.storeIdempotentKey(idempotentKey);
            return queryPaymentStatus(paymentNumber);
        }

        return processPayment(paymentNumber);
    }

    @Transactional
    public PaymentResponse retryFailedPayment(String paymentNumber) {
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentNumber(paymentNumber);
        
        if (paymentOpt.isEmpty()) {
            return PaymentResponse.builder()
                .success(false)
                .message("支付订单不存在")
                .build();
        }

        Payment payment = paymentOpt.get();

        if ("PAID".equals(payment.getStatus())) {
            return queryPaymentStatus(paymentNumber);
        }

        if ("EXPIRED".equals(payment.getStatus())) {
            throw new IllegalStateException("支付订单已过期，无法重试");
        }

        log.info("重试支付: paymentNumber={}", paymentNumber);
        return processPayment(paymentNumber);
    }

    @Transactional
    public void cancelPayment(String paymentNumber, String reason) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));

        if ("PAID".equals(payment.getStatus())) {
            throw new IllegalStateException("已支付的订单无法取消，请申请退款");
        }

        payment.setStatus("CANCELLED");
        payment.setErrorMessage(reason);
        paymentRepository.save(payment);

        pendingPayments.remove(payment.getPlateNumber());

        log.info("取消支付订单: paymentNumber={}, reason={}", paymentNumber, reason);
    }

    public Payment getPaymentByNumber(String paymentNumber) {
        return paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));
    }

    public PaymentResponse queryPaymentStatus(String paymentNumber) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));

        PaymentResponse response = new PaymentResponse();
        response.setSuccess(true);
        response.setPaymentNumber(paymentNumber);
        response.setAmount(payment.getAmount());
        response.setPaid("PAID".equals(payment.getStatus()));
        response.setStatus(payment.getStatus());
        response.setPaidTime(payment.getPaidTime());
        response.setTransactionId(payment.getTransactionId());

        return response;
    }

    @lombok.Builder
    @lombok.Data
    public static class PaymentStatus {
        private String paymentNumber;
        private String status;
        private BigDecimal amount;
        private boolean isPaid;
        private LocalDateTime paidTime;
        private LocalDateTime expiredTime;
        private String transactionId;
        private boolean canRetry;
        private boolean canCancel;
    }

    public PaymentStatus getPaymentStatusDetail(String paymentNumber) {
        Payment payment = getPaymentByNumber(paymentNumber);
        
        boolean canRetry = "PENDING".equals(payment.getStatus()) && 
            (payment.getExpiredTime() == null || LocalDateTime.now().isBefore(payment.getExpiredTime()));
        
        boolean canCancel = "PENDING".equals(payment.getStatus());

        return PaymentStatus.builder()
            .paymentNumber(payment.getPaymentNumber())
            .status(payment.getStatus())
            .amount(payment.getAmount())
            .isPaid("PAID".equals(payment.getStatus()))
            .paidTime(payment.getPaidTime())
            .expiredTime(payment.getExpiredTime())
            .transactionId(payment.getTransactionId())
            .canRetry(canRetry)
            .canCancel(canCancel)
            .build();
    }
}
