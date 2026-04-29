package com.parking.entity;

import com.parking.security.LicensePlateConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "spot_prediction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String spotNumber;

    @Column(nullable = false)
    private Long parkingLotId;

    @Convert(converter = LicensePlateConverter.class)
    @Column(nullable = false)
    private String plateNumber;

    @Column(nullable = false)
    private LocalDateTime occupiedSince;

    @Column(nullable = false)
    private LocalDateTime predictedExitTime;

    @Column(nullable = false)
    private Double confidence;

    @Column
    private LocalDateTime actualExitTime;

    @Column(nullable = false)
    private String predictionStatus = "PREDICTING";

    @Column
    private String predictionModel;

    @Column
    private String historicalPatterns;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_record_id")
    private ParkingRecord parkingRecord;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PredictionStatus {
        PREDICTING,
        PENDING,
        COMPLETED,
        ACCURATE,
        INACCURATE
    }
}