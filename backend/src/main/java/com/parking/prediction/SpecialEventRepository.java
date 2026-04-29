package com.parking.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SpecialEventRepository extends JpaRepository<SpecialEvent, Long> {

    List<SpecialEvent> findByParkingLotIdAndIsActiveTrueOrderByStartTime(Long parkingLotId);

    @Query("SELECT e FROM SpecialEvent e WHERE e.parkingLotId = :parkingLotId AND e.isActive = true AND :time BETWEEN e.startTime AND e.endTime")
    List<SpecialEvent> findActiveEventsAtTime(
        @Param("parkingLotId") Long parkingLotId,
        @Param("time") LocalDateTime time
    );

    @Query("SELECT e FROM SpecialEvent e WHERE e.parkingLotId = :parkingLotId AND e.isActive = true AND e.startTime >= :startTime AND e.endTime <= :endTime ORDER BY e.startTime")
    List<SpecialEvent> findEventsInRange(
        @Param("parkingLotId") Long parkingLotId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    List<SpecialEvent> findByIsActiveTrueOrderByStartTime();

    @Query("SELECT SUM(e.trafficFactor) FROM SpecialEvent e WHERE e.parkingLotId = :parkingLotId AND e.isActive = true AND :time BETWEEN e.startTime AND e.endTime")
    Double getTotalTrafficFactorAtTime(
        @Param("parkingLotId") Long parkingLotId,
        @Param("time") LocalDateTime time
    );
}
