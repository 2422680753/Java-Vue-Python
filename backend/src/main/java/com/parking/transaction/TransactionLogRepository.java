package com.parking.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {

    Optional<TransactionLog> findByTransactionId(String transactionId);

    Optional<TransactionLog> findByBusinessKey(String businessKey);

    List<TransactionLog> findByPaymentNumber(String paymentNumber);

    List<TransactionLog> findByPlateNumber(String plateNumber);

    List<TransactionLog> findByTransactionType(TransactionType transactionType);

    List<TransactionLog> findByState(TransactionState state);

    @Query("SELECT t FROM TransactionLog t WHERE t.state IN :states")
    List<TransactionLog> findByStates(@Param("states") List<TransactionState> states);

    @Query("SELECT t FROM TransactionLog t WHERE t.state = :state AND t.nextRetryTime <= :now")
    List<TransactionLog> findPendingRetry(@Param("state") TransactionState state, 
                                           @Param("now") LocalDateTime now);

    @Query("SELECT t FROM TransactionLog t WHERE t.state IN (:states) AND " +
           "(t.timeoutAt IS NOT NULL AND t.timeoutAt <= :now OR " +
           "t.expireTime IS NOT NULL AND t.expireTime <= :now)")
    List<TransactionLog> findTimeoutTransactions(@Param("states") List<TransactionState> states,
                                                   @Param("now") LocalDateTime now);

    @Query("SELECT t FROM TransactionLog t WHERE t.transactionType = :type AND " +
           "t.state = :state AND t.businessKey = :businessKey")
    Optional<TransactionLog> findByTypeAndStateAndBusinessKey(
            @Param("type") TransactionType type,
            @Param("state") TransactionState state,
            @Param("businessKey") String businessKey);

    @Query("SELECT t FROM TransactionLog t WHERE t.transactionType IN :types AND " +
           "t.state = :state AND t.createdAt >= :since")
    List<TransactionLog> findByTypesAndStateSince(@Param("types") List<TransactionType> types,
                                                     @Param("state") TransactionState state,
                                                     @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM TransactionLog t WHERE t.transactionType = :type AND " +
           "t.businessKey = :businessKey AND t.state IN :states")
    long countByTypeAndBusinessKeyAndStates(@Param("type") TransactionType type,
                                              @Param("businessKey") String businessKey,
                                              @Param("states") List<TransactionState> states);

    @Query("SELECT t FROM TransactionLog t WHERE t.state = :state ORDER BY t.createdAt ASC")
    List<TransactionLog> findOldestByState(@Param("state") TransactionState state);

    @Query("SELECT DISTINCT t.businessKey FROM TransactionLog t WHERE t.transactionType = :type " +
           "AND t.state = :state AND t.createdAt >= :since")
    List<String> findDistinctBusinessKeysByTypeAndStateSince(@Param("type") TransactionType type,
                                                                @Param("state") TransactionState state,
                                                                @Param("since") LocalDateTime since);
}
