package com.parking.repository;

import com.parking.entity.ParkingSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    List<ParkingSpot> findByParkingLotId(Long parkingLotId);

    List<ParkingSpot> findByParkingLotIdAndStatus(Long parkingLotId, String status);

    Optional<ParkingSpot> findBySpotNumberAndParkingLotId(String spotNumber, Long parkingLotId);

    List<ParkingSpot> findByStatus(String status);

    List<ParkingSpot> findByCurrentPlateNumber(String plateNumber);

    @Query("SELECT p FROM ParkingSpot p WHERE p.parkingLot.id = :parkingLotId AND p.status = 'AVAILABLE'")
    List<ParkingSpot> findAvailableSpotsByParkingLotId(@Param("parkingLotId") Long parkingLotId);

    @Query("SELECT p FROM ParkingSpot p WHERE p.estimatedExitTime BETWEEN :startTime AND :endTime AND p.status = 'OCCUPIED'")
    List<ParkingSpot> findSpotsWithEstimatedExitBetween(@Param("startTime") LocalDateTime startTime, 
                                                          @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(p) FROM ParkingSpot p WHERE p.parkingLot.id = :parkingLotId AND p.status = 'AVAILABLE'")
    Long countAvailableByParkingLotId(@Param("parkingLotId") Long parkingLotId);

    @Query("SELECT COUNT(p) FROM ParkingSpot p WHERE p.parkingLot.id = :parkingLotId AND p.status = 'OCCUPIED'")
    Long countOccupiedByParkingLotId(@Param("parkingLotId") Long parkingLotId);
}