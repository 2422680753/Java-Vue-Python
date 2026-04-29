package com.parking.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotentService {

    private final TransactionLogRepository transactionLogRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";
    private static final long IDEMPOTENT_EXPIRE_HOURS = 24;

    public boolean isRequestAlreadyProcessed(String idempotentKey) {
        String redisKey = IDEMPOTENT_KEY_PREFIX + idempotentKey;
        Boolean exists = redisTemplate.hasKey(redisKey);
        
        if (Boolean.TRUE.equals(exists)) {
            log.info("请求已处理 (Redis): {}", idempotentKey);
            return true;
        }
        
        List<TransactionState> processedStates = Arrays.asList(
            TransactionState.SUCCEEDED,
            TransactionState.CONFIRMED,
            TransactionState.COMPLETED
        );
        
        long count = transactionLogRepository.countByTypeAndBusinessKeyAndStates(
            TransactionType.PAYMENT_PROCESS,
            idempotentKey,
            processedStates
        );
        
        if (count > 0) {
            log.info("请求已处理 (数据库): {}", idempotentKey);
            storeIdempotentKey(idempotentKey);
            return true;
        }
        
        return false;
    }

    public Optional<TransactionLog> getExistingTransaction(String businessKey, TransactionType type) {
        List<TransactionState> activeStates = Arrays.asList(
            TransactionState.PENDING,
            TransactionState.PROCESSING,
            TransactionState.TO_BE_CONFIRMED,
            TransactionState.RETRYING
        );
        
        return transactionLogRepository.findByTypeAndStateAndBusinessKey(type, null, businessKey);
    }

    public void storeIdempotentKey(String idempotentKey) {
        String redisKey = IDEMPOTENT_KEY_PREFIX + idempotentKey;
        redisTemplate.opsForValue().set(redisKey, "PROCESSED", Duration.ofHours(IDEMPOTENT_EXPIRE_HOURS));
        log.debug("存储幂等键: {}", idempotentKey);
    }

    @Transactional
    public TransactionLog createTransaction(TransactionType type, String businessKey, 
                                               String plateNumber, String paymentNumber,
                                               String requestData, int maxRetries) {
        if (businessKey != null) {
            Optional<TransactionLog> existing = getExistingTransaction(businessKey, type);
            if (existing.isPresent()) {
                log.warn("已存在进行中的事务: businessKey={}, type={}", businessKey, type);
                return existing.get();
            }
        }
        
        TransactionLog log = new TransactionLog();
        log.setTransactionType(type);
        log.setState(TransactionState.PENDING);
        log.setBusinessKey(businessKey);
        log.setPlateNumber(plateNumber);
        log.setPaymentNumber(paymentNumber);
        log.setRequestData(requestData);
        log.setMaxRetryCount(maxRetries);
        log.setRetryCount(0);
        log.setTimeoutAt(LocalDateTime.now().plusMinutes(30));
        log.setExpireTime(LocalDateTime.now().plusHours(24));
        
        TransactionLog saved = transactionLogRepository.save(log);
        
        log.info("创建事务日志: transactionId={}, type={}, businessKey={}", 
                 saved.getTransactionId(), type, businessKey);
        
        return saved;
    }

    @Transactional
    public void updateTransactionState(String transactionId, TransactionState newState, 
                                         String responseData, String errorMessage) {
        Optional<TransactionLog> optional = transactionLogRepository.findByTransactionId(transactionId);
        
        if (optional.isEmpty()) {
            log.warn("事务不存在: transactionId={}", transactionId);
            return;
        }
        
        TransactionLog log = optional.get();
        TransactionState oldState = log.getState();
        
        log.setState(newState);
        if (responseData != null) {
            log.setResponseData(responseData);
        }
        if (errorMessage != null) {
            log.setErrorMessage(errorMessage);
        }
        
        transactionLogRepository.save(log);
        
        log.info("更新事务状态: transactionId={}, {} -> {}, error={}", 
                 transactionId, oldState, newState, errorMessage != null);
    }

    @Transactional
    public void markTransactionForRetry(String transactionId, String errorMessage) {
        Optional<TransactionLog> optional = transactionLogRepository.findByTransactionId(transactionId);
        
        if (optional.isEmpty()) {
            return;
        }
        
        TransactionLog log = optional.get();
        
        if (log.getRetryCount() >= log.getMaxRetryCount()) {
            log.setState(TransactionState.FAILED);
            log.setErrorMessage("重试次数已达上限: " + errorMessage);
            transactionLogRepository.save(log);
            log.warn("事务重试次数已达上限: transactionId={}", transactionId);
            return;
        }
        
        log.incrementRetry();
        log.setState(TransactionState.RETRYING);
        log.setErrorMessage(errorMessage);
        
        transactionLogRepository.save(log);
        
        log.info("标记事务重试: transactionId={}, retryCount={}, nextRetry={}", 
                 transactionId, log.getRetryCount(), log.getNextRetryTime());
    }

    public boolean checkAndMarkDuplicate(String businessKey, TransactionType type) {
        List<TransactionState> successStates = Arrays.asList(
            TransactionState.SUCCEEDED,
            TransactionState.CONFIRMED
        );
        
        long count = transactionLogRepository.countByTypeAndBusinessKeyAndStates(
            type, businessKey, successStates
        );
        
        if (count > 0) {
            log.warn("检测到重复请求: businessKey={}, type={}", businessKey, type);
            return true;
        }
        
        List<TransactionState> processingStates = Arrays.asList(
            TransactionState.PENDING,
            TransactionState.PROCESSING,
            TransactionState.RETRYING
        );
        
        long processingCount = transactionLogRepository.countByTypeAndBusinessKeyAndStates(
            type, businessKey, processingStates
        );
        
        if (processingCount > 0) {
            log.warn("检测到进行中的事务: businessKey={}, type={}", businessKey, type);
            throw new IllegalStateException("支付处理中，请稍后查询状态");
        }
        
        return false;
    }

    public String generateIdempotentKey(String prefix, Object... params) {
        StringBuilder key = new StringBuilder(prefix);
        for (Object param : params) {
            if (param != null) {
                key.append(":").append(param.toString());
            }
        }
        return key.toString();
    }

    public void clearIdempotentKey(String idempotentKey) {
        String redisKey = IDEMPOTENT_KEY_PREFIX + idempotentKey;
        redisTemplate.delete(redisKey);
        log.debug("清除幂等键: {}", idempotentKey);
    }
}
