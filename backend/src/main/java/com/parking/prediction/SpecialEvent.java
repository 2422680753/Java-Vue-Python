package com.parking.prediction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "special_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column
    private EventType eventType;

    @Column
    private String location;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column
    private Integer expectedVisitors;

    @Column
    private Long parkingLotId;

    @Column(nullable = false)
    private Double trafficFactor = 1.3;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String description;

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

    public boolean isEventActiveAt(LocalDateTime time) {
        if (time == null) {
            return false;
        }
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }

    public double getTrafficImpactAt(LocalDateTime time) {
        if (!isEventActiveAt(time)) {
            return 1.0;
        }
        
        long minutesBeforeStart = java.time.Duration.between(time, startTime).toMinutes();
        long minutesAfterStart = java.time.Duration.between(startTime, time).toMinutes();
        long eventDuration = java.time.Duration.between(startTime, endTime).toMinutes();
        
        double peakFactor = trafficFactor;
        
        if (minutesBeforeStart > 0 && minutesBeforeStart <= 120) {
            double rampUp = (120.0 - minutesBeforeStart) / 120.0;
            peakFactor = 1.0 + (trafficFactor - 1.0) * rampUp;
        } else if (minutesAfterStart > 0) {
            double midEventFactor = 1.0;
            if (minutesAfterStart < 60) {
                midEventFactor = 0.5 + minutesAfterStart / 120.0;
            } else if (minutesAfterStart > eventDuration - 60) {
                long minutesBeforeEnd = eventDuration - minutesAfterStart;
                midEventFactor = minutesBeforeEnd / 60.0;
            } else {
                midEventFactor = 1.0;
            }
            peakFactor = 1.0 + (trafficFactor - 1.0) * midEventFactor;
        }
        
        return peakFactor;
    }

    public enum EventType {
        CONCERT("演唱会", "大型音乐演出"),
        SPORTS("体育赛事", "足球、篮球等比赛"),
        MARKET("集市", "农贸市场、跳蚤市场"),
        EXHIBITION("展览", "展会、博览会"),
        CONFERENCE("会议", "大型会议、论坛"),
        FESTIVAL("节日活动", "庙会、文化节"),
        SHOPPING_PROMOTION("购物促销", "商场促销活动"),
        MOVIE_PREMIERE("电影首映", "电影首映式"),
        THEME_PARK_EVENT("主题公园活动", "游乐园特殊活动"),
        SCHOOL_EVENT("学校活动", "开学、放学高峰期"),
        HOSPITAL_VISIT("医院就诊", "医院门诊高峰期"),
        GOVERNMENT_SERVICE("政务服务", "政务大厅办事高峰期"),
        OTHER("其他", "其他类型活动");

        private final String label;
        private final String description;

        EventType(String label, String description) {
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
