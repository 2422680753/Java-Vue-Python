package com.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotStatusUpdate {

    private String spotNumber;
    private String status;
    private String zone;
    private Integer floor;
    private String plateNumber;
    private Long timestamp;
}
