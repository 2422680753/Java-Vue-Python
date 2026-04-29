package com.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResult {

    private String spotNumber;
    private String plateNumber;
    private LocalDateTime predictedExitTime;
    private double confidence;
    private long currentDurationMinutes;
    private long minutesUntilAvailable;
    private String zone;
    private String status;
}
