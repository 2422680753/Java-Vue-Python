package com.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private String paymentMethod;
    private String plateNumber;
    private Long parkingRecordId;
    private String openId;
    private String notifyUrl;
    private String clientIp;
    private String deviceInfo;
}
