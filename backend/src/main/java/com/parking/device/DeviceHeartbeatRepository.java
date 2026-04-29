package com.parking.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceHeartbeatRepository extends JpaRepository<DeviceHeartbeatLog, Long> {

    List<DeviceHeartbeatLog> findByDeviceIdOrderByHeartbeatTimeDesc(String deviceId);

    List<DeviceHeartbeatLog> findByDeviceIdAndHeartbeatTimeBetweenOrderByHeartbeatTimeDesc(
        String deviceId, LocalDateTime startTime, LocalDateTime endTime);

    DeviceHeartbeatLog findTopByDeviceIdOrderByHeartbeatTimeDesc(String deviceId);

    @Query("SELECT h FROM DeviceHeartbeatLog h WHERE h.deviceId = :deviceId ORDER BY h.heartbeatTime DESC")
    List<DeviceHeartbeatLog> findRecentHeartbeats(@Param("deviceId") String deviceId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT h FROM DeviceHeartbeatLog h WHERE h.heartbeatTime >= :threshold AND h.success = false")
    List<DeviceHeartbeatLog> findFailedHeartbeatsSince(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(h) FROM DeviceHeartbeatLog h WHERE h.deviceId = :deviceId AND h.success = false AND h.heartbeatTime >= :since")
    long countFailedSince(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(h.responseTimeMs) FROM DeviceHeartbeatLog h WHERE h.deviceId = :deviceId AND h.heartbeatTime >= :since")
    Double getAverageResponseTimeSince(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    @Query("SELECT MAX(h.responseTimeMs) FROM DeviceHeartbeatLog h WHERE h.deviceId = :deviceId AND h.heartbeatTime >= :since")
    Integer getMaxResponseTimeSince(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    void deleteByHeartbeatTimeBefore(LocalDateTime cutoffTime);
}
