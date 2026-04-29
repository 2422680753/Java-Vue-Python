package com.parking.repository;

import com.parking.entity.ParkingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingRecordRepository extends JpaRepository<ParkingRecord, Long> {

    Optional<ParkingRecord> findByRecordNumber(String recordNumber);

    @Query("SELECT p FROM ParkingRecord p WHERE p.plateNumber = :plateNumber AND p.status = 'ACTIVE'")
    Optional<ParkingRecord> findActiveByPlateNumber(@Param("plateNumber") String plateNumber);

    List<ParkingRecord> findByStatus(String status);

    List<ParkingRecord> findByParkingLotId(Long parkingLotId);

    @Query("SELECT p FROM ParkingRecord p WHERE p.entryTime BETWEEN :startTime AND :endTime")
    List<ParkingRecord> findByEntryTimeBetween(@Param("startTime") LocalDateTime startTime, 
                                                 @Param("endTime") LocalDateTime endTime);

    @Query("SELECT p FROM ParkingRecord p WHERE p.exitTime BETWEEN :startTime AND :endTime")
    List<ParkingRecord> findByExitTimeBetween(@Param("startTime") LocalDateTime startTime, 
                                                @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(p) FROM ParkingRecord p WHERE p.parkingLot.id = :parkingLotId AND p.status = 'ACTIVE'")
    Long countActiveByParkingLotId(@Param("parkingLotId") Long parkingLotId);

    @Query("SELECT SUM(p.amount) FROM ParkingRecord p WHERE p.parkingLot.id = :parkingLotId AND p.status = 'COMPLETED' AND p.exitTime BETWEEN :startTime AND :endTime")
    Double sumRevenueByParkingLotIdAndTimeRange(@Param("parkingLotId") Long parkingLotId,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    @Query("SELECT p FROM ParkingRecord p WHERE p.plateNumber = :plateNumber ORDER BY p.entryTime DESC")
    List<ParkingRecord> findAllByPlateNumberOrderByEntryTimeDesc(@Param("plateNumber") String plateNumber);
}