package com.parking.entity;

import com.parking.security.LicensePlateConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String paymentNumber;

    @Convert(converter = LicensePlateConverter.class)
    @Column(nullable = false)
    private String plateNumber;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal paidAmount;

    @Column(nullable = false)
    private String paymentMethod;

    @Column
    private String qrCodeUrl;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column
    private String transactionId;

    @Column
    private LocalDateTime paidTime;

    @Column
    private LocalDateTime expiredTime;

    @Column
    private String paymentProvider;

    @Column
    private String note;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_record_id")
    private ParkingRecord parkingRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (paymentNumber == null) {
            paymentNumber = generatePaymentNumber();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generatePaymentNumber() {
        return "PY" + System.currentTimeMillis();
    }

    public enum Status {
        PENDING,
        PAID,
        EXPIRED,
        CANCELLED,
        REFUNDED
    }

    public enum PaymentMethod {
        WECHAT,
        ALIPAY,
        BALANCE,
        CASH,
        CARD
    }
}