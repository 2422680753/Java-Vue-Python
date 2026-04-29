package com.parking.prediction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "holiday")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HolidayType type;

    @Column(nullable = false)
    private Boolean isPeakDay = true;

    @Column(nullable = false)
    private Double trafficFactor = 1.5;

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

    public enum HolidayType {
        NATIONAL_HOLIDAY("法定节假日", "如春节、国庆等长假"),
        WEEKEND("周末", "周六、周日"),
        SPECIAL_EVENT("特殊活动日", "如演唱会、运动会等"),
        CUSTOM_HOLIDAY("自定义节假日", "根据业务需求自定义");

        private final String label;
        private final String description;

        HolidayType(String label, String description) {
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
