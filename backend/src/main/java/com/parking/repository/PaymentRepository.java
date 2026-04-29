package com.parking.repository;

import com.parking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentNumber(String paymentNumber);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByPlateNumber(String plateNumber);

    List<Payment> findByStatus(String status);

    List<Payment> findByPaymentMethod(String paymentMethod);

    @Query("SELECT p FROM Payment p WHERE p.parkingRecord.id = :parkingRecordId")
    Optional<Payment> findByParkingRecordId(@Param("parkingRecordId") Long parkingRecordId);

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startTime AND :endTime")
    List<Payment> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                          @Param("endTime") LocalDateTime endTime);

    @Query("SELECT SUM(p.paidAmount) FROM Payment p WHERE p.status = 'PAID' AND p.paidTime BETWEEN :startTime AND :endTime")
    Double sumPaidAmountByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                     @Param("endTime") LocalDateTime endTime);

    @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.expiredTime < :now")
    List<Payment> findExpiredPayments(@Param("now") LocalDateTime now);
}