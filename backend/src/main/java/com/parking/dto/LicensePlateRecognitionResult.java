package com.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LicensePlateRecognitionResult {

    private boolean success;
    private String message;
    private String plateNumber;
    private String province;
    private String city;
    private String vehicleType;
    private String color;
    private double confidence;
    private String imageUrl;
    private LocalDateTime timestamp;
    private String processingTime;
    private String algorithmVersion;
}
