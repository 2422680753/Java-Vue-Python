package com.parking.prediction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false)
    private Integer recordHour = 0;

    @Column
    private Double temperature;

    @Column
    private Integer humidity;

    @Column
    private Double precipitation;

    @Column
    private Double windSpeed;

    @Column
    private Double visibility;

    @Enumerated(EnumType.STRING)
    @Column
    private WeatherCondition weatherCondition;

    @Column
    private Integer uvIndex;

    @Column
    private Integer pressure;

    @Column
    private String description;

    @Column
    private String dataSource = "LOCAL";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public double getTrafficImpactFactor() {
        if (weatherCondition == null) {
            return 1.0;
        }
        
        return switch (weatherCondition) {
            case CLEAR, SUNNY, PARTLY_CLOUDY, CLOUDY -> 1.0;
            case LIGHT_RAIN, DRIZZLE, MIST -> 1.1;
            case RAIN, HEAVY_RAIN, THUNDERSTORM -> 1.3;
            case FOG, HEAVY_FOG -> 1.5;
            case SNOW, HEAVY_SNOW, BLIZZARD -> 1.6;
            case HAIL, TYPHOON, HURRICANE -> 1.8;
        };
    }

    public boolean isBadWeather() {
        if (weatherCondition == null) {
            return false;
        }
        
        return switch (weatherCondition) {
            case RAIN, HEAVY_RAIN, THUNDERSTORM, FOG, HEAVY_FOG, SNOW, HEAVY_SNOW, 
                 BLIZZARD, HAIL, TYPHOON, HURRICANE -> true;
            default -> false;
        };
    }

    public enum WeatherCondition {
        SUNNY("晴天"),
        CLEAR("晴朗"),
        PARTLY_CLOUDY("少云"),
        CLOUDY("多云"),
        OVERCAST("阴天"),
        LIGHT_RAIN("小雨"),
        RAIN("中雨"),
        HEAVY_RAIN("大雨"),
        DRIZZLE("毛毛雨"),
        THUNDERSTORM("雷阵雨"),
        FOG("雾"),
        HEAVY_FOG("浓雾"),
        MIST("薄雾"),
        SNOW("小雪"),
        HEAVY_SNOW("大雪"),
        BLIZZARD("暴雪"),
        HAIL("冰雹"),
        TYPHOON("台风"),
        HURRICANE("飓风"),
        SANDSTORM("沙尘暴"),
        DUST("浮尘"),
        HAZE("霾");

        private final String label;

        WeatherCondition(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
