package com.parking.controller;

import com.parking.dto.ApiResponse;
import com.parking.dto.PaymentRequest;
import com.parking.dto.PaymentResponse;
import com.parking.entity.Payment;
import com.parking.service.PaymentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "支付管理")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    @ApiOperation("创建支付订单")
    public ApiResponse<PaymentResponse> createPayment(
            @RequestParam Long parkingRecordId,
            @RequestBody(required = false) PaymentRequest request) {
        
        log.info("创建支付订单: 停车记录ID={}", parkingRecordId);
        
        if (request == null) {
            request = new PaymentRequest();
            request.setPaymentMethod("WECHAT");
        }
        
        try {
            PaymentResponse response = paymentService.createPaymentOrder(parkingRecordId, request);
            return ApiResponse.success(response);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建支付订单失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/pay/{paymentNumber}")
    @ApiOperation("处理支付（模拟支付回调）")
    public ApiResponse<PaymentResponse> processPayment(@PathVariable String paymentNumber) {
        log.info("处理支付: 支付单号={}", paymentNumber);
        
        try {
            PaymentResponse response = paymentService.processPayment(paymentNumber);
            return ApiResponse.success("支付成功", response);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("支付处理失败: {}", e.getMessage(), e);
            return ApiResponse.error("支付失败: " + e.getMessage());
        }
    }

    @GetMapping("/status/{paymentNumber}")
    @ApiOperation("查询支付状态")
    public ApiResponse<PaymentResponse> queryPaymentStatus(@PathVariable String paymentNumber) {
        try {
            PaymentResponse response = paymentService.queryPaymentStatus(paymentNumber);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/detail/{paymentNumber}")
    @ApiOperation("获取支付详情")
    public ApiResponse<Payment> getPaymentDetail(@PathVariable String paymentNumber) {
        try {
            Payment payment = paymentService.getPaymentByNumber(paymentNumber);
            return ApiResponse.success(payment);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/qrcode/{paymentNumber}")
    @ApiOperation("获取支付二维码URL")
    public ApiResponse<String> getPaymentQrCode(@PathVariable String paymentNumber) {
        try {
            Payment payment = paymentService.getPaymentByNumber(paymentNumber);
            return ApiResponse.success(payment.getQrCodeUrl());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/simulate-pay")
    @ApiOperation("模拟扫码支付（测试用）")
    public ApiResponse<PaymentResponse> simulatePayment(
            @RequestParam String paymentNumber,
            @RequestParam(required = false) String paymentMethod) {
        
        log.info("模拟支付: 支付单号={}, 方式={}", paymentNumber, paymentMethod);
        
        try {
            PaymentResponse response = paymentService.processPayment(paymentNumber);
            return ApiResponse.success("模拟支付成功", response);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("模拟支付失败: {}", e.getMessage(), e);
            return ApiResponse.error("支付失败: " + e.getMessage());
        }
    }
}