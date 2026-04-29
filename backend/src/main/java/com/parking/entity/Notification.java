package com.parking.entity;

import com.parking.security.LicensePlateConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String priority = "NORMAL";

    @Column
    private Long targetUserId;

    @Column
    private String targetPhone;

    @Convert(converter = LicensePlateConverter.class)
    @Column
    private String plateNumber;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column
    private LocalDateTime sentTime;

    @Column
    private LocalDateTime readTime;

    @Column
    private String channel;

    @Column
    private String errorMessage;

    @Column
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Type {
        PEAK_WARNING,
        SPOT_AVAILABLE,
        PAYMENT_REMINDER,
        PAYMENT_SUCCESS,
        ENTRY_CONFIRM,
        EXIT_CONFIRM,
        SYSTEM_MESSAGE,
        PROMOTION
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum Status {
        PENDING,
        SENT,
        READ,
        FAILED,
        CANCELLED
    }

    public enum Channel {
        SMS,
        WECHAT,
        APP,
        EMAIL,
        SCREEN
    }
}