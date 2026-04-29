package com.parking.service;

import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingSpot;
import com.parking.repository.ParkingLotRepository;
import com.parking.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParkingService {

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final WebSocketService webSocketService;

    public List<ParkingLot> getAllParkingLots() {
        return parkingLotRepository.findAll();
    }

    public Optional<ParkingLot> getParkingLotById(Long id) {
        return parkingLotRepository.findById(id);
    }

    @Transactional
    public ParkingLot createParkingLot(ParkingLot parkingLot) {
        parkingLot.setOccupiedSpots(0);
        parkingLot.setStatus("ACTIVE");
        
        ParkingLot savedLot = parkingLotRepository.save(parkingLot);
        
        initializeSpots(savedLot);
        
        log.info("创建停车场: {}, 总车位: {}", savedLot.getName(), savedLot.getTotalSpots());
        return savedLot;
    }

    private void initializeSpots(ParkingLot parkingLot) {
        String[] zones = {"A", "B", "C", "D"};
        int spotsPerZone = parkingLot.getTotalSpots() / zones.length;
        int remaining = parkingLot.getTotalSpots() % zones.length;

        for (int z = 0; z < zones.length; z++) {
            int spotsForThisZone = spotsPerZone + (z < remaining ? 1 : 0);
            for (int i = 1; i <= spotsForThisZone; i++) {
                ParkingSpot spot = new ParkingSpot();
                spot.setSpotNumber(zones[z] + String.format("%03d", i));
                spot.setZone(zones[z]);
                spot.setFloor(1);
                spot.setType("STANDARD");
                spot.setStatus("AVAILABLE");
                spot.setParkingLot(parkingLot);
                parkingSpotRepository.save(spot);
            }
        }
    }

    public List<ParkingSpot> getSpotsByParkingLot(Long parkingLotId) {
        return parkingSpotRepository.findByParkingLotId(parkingLotId);
    }

    public List<ParkingSpot> getAvailableSpots(Long parkingLotId) {
        return parkingSpotRepository.findAvailableSpotsByParkingLotId(parkingLotId);
    }

    public Long countAvailableSpots(Long parkingLotId) {
        return parkingSpotRepository.countAvailableByParkingLotId(parkingLotId);
    }

    public Long countOccupiedSpots(Long parkingLotId) {
        return parkingSpotRepository.countOccupiedByParkingLotId(parkingLotId);
    }

    @Transactional
    public ParkingSpot updateSpotStatus(Long spotId, String status) {
        ParkingSpot spot = parkingSpotRepository.findById(spotId)
                .orElseThrow(() -> new IllegalArgumentException("车位不存在: " + spotId));

        String oldStatus = spot.getStatus();
        spot.setStatus(status);
        
        ParkingSpot updatedSpot = parkingSpotRepository.save(spot);

        if (!oldStatus.equals(status)) {
            webSocketService.sendSpotUpdate(spot.getParkingLot().getId(), updatedSpot, status);
            webSocketService.sendParkingLotUpdate(spot.getParkingLot().getId());
        }

        log.info("更新车位 {} 状态: {} -> {}", spot.getSpotNumber(), oldStatus, status);
        return updatedSpot;
    }

    public void broadcastParkingLotStatus(Long parkingLotId) {
        webSocketService.sendParkingLotUpdate(parkingLotId);
    }
}