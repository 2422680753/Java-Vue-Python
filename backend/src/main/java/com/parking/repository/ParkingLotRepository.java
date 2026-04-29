package com.parking.repository;

import com.parking.entity.ParkingLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

    List<ParkingLot> findByStatus(String status);

    Optional<ParkingLot> findByName(String name);

    @Query("SELECT p FROM ParkingLot p WHERE p.latitude BETWEEN ?1 AND ?2 AND p.longitude BETWEEN ?3 AND ?4")
    List<ParkingLot> findByLocationRange(Double minLat, Double maxLat, Double minLon, Double maxLon);

    @Query("SELECT p FROM ParkingLot p WHERE (p.totalSpots - p.occupiedSpots) > 0")
    List<ParkingLot> findWithAvailableSpots();
}