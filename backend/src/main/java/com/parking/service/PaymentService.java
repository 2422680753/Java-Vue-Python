package com.parking.service;

import com.parking.dto.PaymentRequest;
import com.parking.dto.PaymentResponse;
import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingRecord;
import com.parking.entity.Payment;
import com.parking.repository.ParkingLotRepository;
import com.parking.repository.ParkingRecordRepository;
import com.parking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

        Payment payment = new Payment();
        payment.setPlateNumber(record.getPlateNumber());
        payment.setAmount(amount);
        payment.setPaidAmount(amount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus("PENDING");
        payment.setPaymentProvider("SIMULATED");
        payment.setExpiredTime(LocalDateTime.now().plusMinutes(15));
        payment.setParkingRecord(record);

        String paymentNumber = "PY" + System.currentTimeMillis();
        payment.setPaymentNumber(paymentNumber);
        payment.setQrCodeUrl(generateQrCodeUrl(paymentNumber, amount));

        Payment savedPayment = paymentRepository.save(payment);
        pendingPayments.put(record.getPlateNumber(), savedPayment);

        record.setAmount(amount);
        record.setStatus("PAID");
        parkingRecordRepository.save(record);

        PaymentResponse response = new PaymentResponse();
        response.setSuccess(true);
        response.setPaymentNumber(paymentNumber);
        response.setQrCodeUrl(savedPayment.getQrCodeUrl());
        response.setAmount(amount);
        response.setExpiredTime(savedPayment.getExpiredTime());
        response.setMessage("支付订单创建成功，请在15分钟内完成支付");

        log.info("创建支付订单: {}, 车牌: {}, 金额: {}", paymentNumber, record.getPlateNumber(), amount);
        return response;
    }

    private String generateQrCodeUrl(String paymentNumber, BigDecimal amount) {
        return qrCodeBaseUrl + paymentNumber + "?amount=" + amount;
    }

    @Transactional
    public PaymentResponse processPayment(String paymentNumber) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));

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

        PaymentResponse response = new PaymentResponse();
        response.setSuccess(true);
        response.setPaymentNumber(paymentNumber);
        response.setAmount(savedPayment.getAmount());
        response.setPaid(true);
        response.setPaidTime(savedPayment.getPaidTime());
        response.setTransactionId(savedPayment.getTransactionId());
        response.setMessage("支付成功，请在15分钟内离场");

        log.info("支付成功: {}, 车牌: {}, 金额: {}", paymentNumber, payment.getPlateNumber(), payment.getAmount());
        return response;
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
}