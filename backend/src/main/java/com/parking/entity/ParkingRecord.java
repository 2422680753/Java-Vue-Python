package com.parking.entity;

import com.parking.security.LicensePlateConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String recordNumber;

    @Convert(converter = LicensePlateConverter.class)
    @Column(nullable = false)
    private String plateNumber;

    @Column
    private String vehicleType;

    @Column
    private String vehicleColor;

    @Column(nullable = false)
    private String entryImageUrl;

    @Column
    private String exitImageUrl;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    @Column
    private LocalDateTime exitTime;

    @Column
    private Long durationMinutes;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column
    private String paymentMethod;

    @Column
    private String paymentTransactionId;

    @Column
    private LocalDateTime paymentTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column
    private String discountReason;

    @Column
    private String entryGateId;

    @Column
    private String exitGateId;

    @Column
    private String operatorEntry;

    @Column
    private String operatorExit;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id")
    private ParkingLot parkingLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_spot_id")
    private ParkingSpot parkingSpot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (recordNumber == null) {
            recordNumber = generateRecordNumber();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateRecordNumber() {
        return "PR" + System.currentTimeMillis();
    }

    public enum Status {
        ACTIVE,
        COMPLETED,
        CANCELLED,
        PAID
    }
}