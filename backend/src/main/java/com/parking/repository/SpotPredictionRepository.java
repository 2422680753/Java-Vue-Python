package com.parking.repository;

import com.parking.entity.SpotPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpotPredictionRepository extends JpaRepository<SpotPrediction, Long> {

    List<SpotPrediction> findByParkingLotId(Long parkingLotId);

    List<SpotPrediction> findBySpotNumberAndParkingLotId(String spotNumber, Long parkingLotId);

    Optional<SpotPrediction> findByParkingRecordId(Long parkingRecordId);

    List<SpotPrediction> findByPredictionStatus(String predictionStatus);

    @Query("SELECT p FROM SpotPrediction p WHERE p.predictedExitTime BETWEEN :startTime AND :endTime AND p.predictionStatus = 'PENDING'")
    List<SpotPrediction> findUpcomingExits(@Param("startTime") LocalDateTime startTime, 
                                             @Param("endTime") LocalDateTime endTime);

    @Query("SELECT p FROM SpotPrediction p WHERE p.parkingLotId = :parkingLotId AND p.predictedExitTime BETWEEN :now AND :twoHoursLater AND p.predictionStatus = 'PENDING'")
    List<SpotPrediction> findPredictionsForNextTwoHours(@Param("parkingLotId") Long parkingLotId,
                                                          @Param("now") LocalDateTime now,
                                                          @Param("twoHoursLater") LocalDateTime twoHoursLater);

    @Query("SELECT AVG(CASE WHEN p.actualExitTime IS NOT NULL AND ABS(TIMESTAMPDIFF(MINUTE, p.predictedExitTime, p.actualExitTime)) <= 15 THEN 1 ELSE 0 END) FROM SpotPrediction p WHERE p.actualExitTime IS NOT NULL")
    Double calculatePredictionAccuracy();
}