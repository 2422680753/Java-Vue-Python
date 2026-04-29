package com.parking.controller;

import com.parking.transaction.OrderStatusVerificationService;
import com.parking.transaction.TransactionCompensationService;
import com.parking.transaction.TransactionLog;
import com.parking.transaction.TransactionState;
import com.parking.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionCompensationService compensationService;
    private final OrderStatusVerificationService verificationService;

    @GetMapping("/logs")
    public ResponseEntity<List<TransactionLog>> getTransactionLogs(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) TransactionState state,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<TransactionLog> logs;
        
        if (plateNumber != null) {
            logs = compensationService.findByPlateNumber(plateNumber)
                    .stream()
                    .limit(limit)
                    .toList();
        } else if (businessKey != null) {
            logs = compensationService.findByBusinessKey(businessKey);
        } else if (state != null) {
            logs = compensationService.findByState(state)
                    .stream()
                    .limit(limit)
                    .toList();
        } else if (type != null) {
            logs = compensationService.findByType(type)
                    .stream()
                    .limit(limit)
                    .toList();
        } else {
            logs = compensationService.findAll().stream()
                    .limit(limit)
                    .toList();
        }
        
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/{transactionId}")
    public ResponseEntity<TransactionLog> getTransactionLog(@PathVariable String transactionId) {
        return compensationService.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/retry/{transactionId}")
    public ResponseEntity<Map<String, Object>> retryTransaction(@PathVariable String transactionId) {
        log.info("手动触发重试事务: transactionId={}", transactionId);
        
        boolean success = compensationService.retryTransaction(transactionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("transactionId", transactionId);
        result.put("message", success ? "重试触发成功" : "事务不可重试或已完成");
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/compensate/{transactionId}")
    public ResponseEntity<Map<String, Object>> compensateTransaction(@PathVariable String transactionId) {
        log.info("手动触发补偿事务: transactionId={}", transactionId);
        
        boolean success = compensationService.compensateTransaction(transactionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("transactionId", transactionId);
        result.put("message", success ? "补偿触发成功" : "事务不可补偿");
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/mark-failed/{transactionId}")
    public ResponseEntity<Map<String, Object>> markTransactionFailed(
            @PathVariable String transactionId,
            @RequestBody(required = false) Map<String, String> body) {
        
        String errorMessage = body != null ? body.get("errorMessage") : "手动标记失败";
        
        log.info("手动标记事务失败: transactionId={}, error={}", transactionId, errorMessage);
        
        var logOpt = compensationService.findByTransactionId(transactionId);
        if (logOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        compensationService.markTransactionFailed(transactionId, errorMessage);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("transactionId", transactionId);
        result.put("message", "事务已标记为失败");
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTransactionStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("pendingCount", compensationService.findByState(TransactionState.PENDING).size());
        stats.put("processingCount", compensationService.findByState(TransactionState.PROCESSING).size());
        stats.put("succeededCount", compensationService.findByState(TransactionState.SUCCEEDED).size());
        stats.put("failedCount", compensationService.findByState(TransactionState.FAILED).size());
        stats.put("compensatingCount", compensationService.findByState(TransactionState.COMPENSATING).size());
        stats.put("compensatedCount", compensationService.findByState(TransactionState.COMPENSATED).size());
        
        stats.put("retryQueuedCount", compensationService.findByState(TransactionState.RETRY_QUEUED).size());
        stats.put("compensationQueuedCount", compensationService.findByState(TransactionState.COMPENSATION_QUEUED).size());
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/verify-order/{paymentNumber}")
    public ResponseEntity<OrderStatusVerificationService.VerificationResult> verifyOrder(
            @PathVariable String paymentNumber) {
        log.info("人工校验订单状态: paymentNumber={}", paymentNumber);
        
        var result = verificationService.verifyOrderStatus(paymentNumber);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/resolve-duplicate/{plateNumber}")
    public ResponseEntity<Map<String, Object>> resolveDuplicatePayment(
            @PathVariable String plateNumber) {
        log.info("检查并解决重复支付: plateNumber={}", plateNumber);
        
        verificationService.resolveDuplicatePayment(plateNumber);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("plateNumber", plateNumber);
        result.put("message", "重复支付检查完成，如有多笔成功支付已标记待退款");
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/run-retry-queue")
    public ResponseEntity<Map<String, Object>> runRetryQueueImmediately() {
        log.info("手动触发重试队列处理");
        
        compensationService.processRetryQueue();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "重试队列处理已执行");
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/run-compensation-check")
    public ResponseEntity<Map<String, Object>> runCompensationCheckImmediately() {
        log.info("手动触发补偿检查");
        
        compensationService.checkCompensationNeeded();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "补偿检查已执行");
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/run-timeout-check")
    public ResponseEntity<Map<String, Object>> runTimeoutCheckImmediately() {
        log.info("手动触发超时检查");
        
        compensationService.checkTimeoutTransactions();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "超时检查已执行");
        
        return ResponseEntity.ok(result);
    }
}
