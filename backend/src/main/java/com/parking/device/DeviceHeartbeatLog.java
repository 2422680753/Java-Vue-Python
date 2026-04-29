package com.parking.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_heartbeat")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceHeartbeatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private LocalDateTime heartbeatTime;

    @Column
    private String ipAddress;

    @Column
    private Integer cpuUsage;

    @Column
    private Integer memoryUsage;

    @Column
    private Integer diskUsage;

    @Column
    private Integer networkLatency;

    @Column
    private Double temperature;

    @Column
    private Integer batteryLevel;

    @Column
    @Enumerated(EnumType.STRING)
    private Device.DeviceStatus status;

    @Column
    @Enumerated(EnumType.STRING)
    private Device.DeviceHealth health;

    @Column
    private String statusMessage;

    @Column
    private String metricsJson;

    @Column(nullable = false)
    private Boolean success = true;

    @Column
    private String errorMessage;

    @Column
    private Long responseTimeMs;

    @Column
    private String versionInfo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (heartbeatTime == null) {
            heartbeatTime = LocalDateTime.now();
        }
    }

    @Data
    @Builder
    public static class HeartbeatMetrics {
        private Integer cpuUsage;
        private Integer memoryUsage;
        private Integer diskUsage;
        private Integer networkLatency;
        private Double temperature;
        private Integer batteryLevel;
    }
}
