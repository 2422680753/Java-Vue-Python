package com.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private boolean success;
    private String message;
    private String paymentNumber;
    private String qrCodeUrl;
    private BigDecimal amount;
    private LocalDateTime expiredTime;
    private boolean paid;
    private String status;
    private LocalDateTime paidTime;
    private String transactionId;
    private String paymentMethod;
}
