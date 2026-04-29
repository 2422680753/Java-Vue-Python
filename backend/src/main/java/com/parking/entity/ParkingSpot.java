package com.parking.entity;

import com.parking.security.LicensePlateConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_spot")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String spotNumber;

    @Column(nullable = false)
    private String zone;

    @Column
    private Integer floor;

    @Column(nullable = false)
    private String type = "STANDARD";

    @Column(nullable = false)
    private String status = "AVAILABLE";

    @Convert(converter = LicensePlateConverter.class)
    @Column
    private String currentPlateNumber;

    @Column
    private LocalDateTime occupiedSince;

    @Column
    private LocalDateTime estimatedExitTime;

    @Column
    private Double confidence = 0.0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id", nullable = false)
    private ParkingLot parkingLot;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        AVAILABLE,
        OCCUPIED,
        RESERVED,
        MAINTENANCE
    }

    public enum Type {
        STANDARD,
        HANDICAPPED,
        ELECTRIC,
        VIP,
        LARGE
    }
}