package com.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLotStatus {

    private Long parkingLotId;
    private String parkingLotName;
    private Integer totalSpots;
    private Integer occupiedSpots;
    private Integer availableSpots;
    private Double occupancyRate;
    private LocalDateTime lastUpdated;
    private boolean isPeakHour;
    private List<PredictionResult> upcomingAvailableSpots;
}
