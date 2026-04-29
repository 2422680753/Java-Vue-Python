package com.parking.device;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String deviceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceType type;

    @Column
    private String location;

    @Column
    private Long parkingLotId;

    @Column
    private String gateId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceStatus status = DeviceStatus.OFFLINE;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceHealth health = DeviceHealth.UNKNOWN;

    @Column
    private String ipAddress;

    @Column
    private String macAddress;

    @Column
    private String serialNumber;

    @Column
    private String model;

    @Column
    private String firmwareVersion;

    @Column
    private LocalDateTime lastHeartbeatTime;

    @Column
    private Integer heartbeatInterval = 5;

    @Column
    private LocalDateTime lastStatusChangeTime;

    @Column
    private String lastError;

    @Column
    private Integer errorCount = 0;

    @Column
    private Integer maxRetries = 3;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column
    private String backupDeviceId;

    @Column
    private Integer priority = 0;

    @Column
    @Enumerated(EnumType.STRING)
    private DeviceRole role = DeviceRole.PRIMARY;

    @Column
    private String configJson;

    @Column
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastStatusChangeTime == null) {
            lastStatusChangeTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isOnline() {
        return DeviceStatus.ONLINE.equals(status);
    }

    public boolean isHealthy() {
        return DeviceHealth.HEALTHY.equals(health);
    }

    public boolean needsAttention() {
        return DeviceHealth.DEGRADED.equals(health) || DeviceHealth.UNHEALTHY.equals(health);
    }

    public void updateStatus(DeviceStatus newStatus) {
        if (!this.status.equals(newStatus)) {
            this.status = newStatus;
            this.lastStatusChangeTime = LocalDateTime.now();
        }
    }

    public void updateHealth(DeviceHealth newHealth) {
        if (!this.health.equals(newHealth)) {
            this.health = newHealth;
            this.lastStatusChangeTime = LocalDateTime.now();
        }
    }

    public void recordError(String errorMessage) {
        this.lastError = errorMessage;
        this.errorCount++;
    }

    public void clearErrors() {
        this.lastError = null;
        this.errorCount = 0;
    }

    public enum DeviceType {
        CAMERA("摄像头", "车牌识别摄像头"),
        INFRARED_CAMERA("红外摄像头", "红外辅助摄像头"),
        BARRIER_GATE("道闸", "入口/出口道闸"),
        QR_SCANNER("二维码扫描器", "支付二维码扫描器"),
        RFID_READER("RFID读卡器", "RFID标签读卡器"),
        SENSOR("传感器", "车位/环境传感器"),
        DISPLAY("显示屏", "LED/LCD显示屏"),
        GATE_CONTROLLER("闸机控制器", "闸机控制单元"),
        OTHER("其他", "其他类型设备");

        private final String label;
        private final String description;

        DeviceType(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum DeviceStatus {
        ONLINE("在线", "设备正常运行"),
        OFFLINE("离线", "设备无响应"),
        MAINTENANCE("维护中", "设备正在维护"),
        ERROR("错误", "设备发生错误"),
        DISABLED("已禁用", "设备已被禁用");

        private final String label;
        private final String description;

        DeviceStatus(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum DeviceHealth {
        HEALTHY("健康", "设备运行正常"),
        DEGRADED("降级", "设备部分功能可用"),
        UNHEALTHY("异常", "设备需要关注"),
        CRITICAL("严重", "设备需要立即处理"),
        UNKNOWN("未知", "设备状态未知");

        private final String label;
        private final String description;

        DeviceHealth(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum DeviceRole {
        PRIMARY("主设备", "主要工作设备"),
        BACKUP("备用设备", "故障时切换到此设备"),
        REDUNDANT("冗余设备", "同时工作互为备份");

        private final String label;
        private final String description;

        DeviceRole(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }
}
