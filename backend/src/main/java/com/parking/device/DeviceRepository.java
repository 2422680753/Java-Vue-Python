package com.parking.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceId(String deviceId);

    List<Device> findByParkingLotId(Long parkingLotId);

    List<Device> findByType(Device.DeviceType type);

    List<Device> findByStatus(Device.DeviceStatus status);

    List<Device> findByHealth(Device.DeviceHealth health);

    List<Device> findByParkingLotIdAndType(Long parkingLotId, Device.DeviceType type);

    List<Device> findByParkingLotIdAndStatus(Long parkingLotId, Device.DeviceStatus status);

    List<Device> findByParkingLotIdAndTypeAndStatus(Long parkingLotId, Device.DeviceType type, Device.DeviceStatus status);

    List<Device> findByTypeAndStatus(Device.DeviceType type, Device.DeviceStatus status);

    List<Device> findByTypeAndHealth(Device.DeviceType type, Device.DeviceHealth health);

    @Query("SELECT d FROM Device d WHERE d.parkingLotId = :parkingLotId AND d.type = :type AND d.status = :status AND d.health = :health")
    List<Device> findHealthyDevicesByLotAndType(
        @Param("parkingLotId") Long parkingLotId,
        @Param("type") Device.DeviceType type,
        @Param("status") Device.DeviceStatus status,
        @Param("health") Device.DeviceHealth health
    );

    @Query("SELECT d FROM Device d WHERE d.type = :type AND d.status = :status AND d.health = :health ORDER BY d.priority DESC")
    List<Device> findHealthyDevicesByType(
        @Param("type") Device.DeviceType type,
        @Param("status") Device.DeviceStatus status,
        @Param("health") Device.DeviceHealth health
    );

    @Query("SELECT d FROM Device d WHERE d.gateId = :gateId AND d.type = :type ORDER BY d.priority DESC")
    List<Device> findByGateIdAndTypeOrderByPriority(
        @Param("gateId") String gateId,
        @Param("type") Device.DeviceType type
    );

    @Query("SELECT d FROM Device d WHERE d.backupDeviceId = :primaryDeviceId")
    List<Device> findBackupDevicesFor(@Param("primaryDeviceId") String primaryDeviceId);

    @Query("SELECT d FROM Device d WHERE d.lastHeartbeatTime < :threshold OR d.lastHeartbeatTime IS NULL")
    List<Device> findDevicesWithStaleHeartbeat(@Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT COUNT(d) FROM Device d WHERE d.parkingLotId = :parkingLotId AND d.type = :type AND d.status = :status")
    long countByLotAndTypeAndStatus(
        @Param("parkingLotId") Long parkingLotId,
        @Param("type") Device.DeviceType type,
        @Param("status") Device.DeviceStatus status
    );

    @Query("SELECT COUNT(d) FROM Device d WHERE d.type = :type AND d.health = :health")
    long countByTypeAndHealth(@Param("type") Device.DeviceType type, @Param("health") Device.DeviceHealth health);

    Optional<Device> findFirstByParkingLotIdAndTypeAndStatusAndHealthOrderByPriorityDesc(
        Long parkingLotId,
        Device.DeviceType type,
        Device.DeviceStatus status,
        Device.DeviceHealth health
    );

    Optional<Device> findFirstByTypeAndStatusAndHealthOrderByPriorityDesc(
        Device.DeviceType type,
        Device.DeviceStatus status,
        Device.DeviceHealth health
    );

    List<Device> findByEnabledTrue();

    List<Device> findByEnabledTrueAndStatus(Device.DeviceStatus status);

    List<Device> findByEnabledTrueAndHealthIn(List<Device.DeviceHealth> healths);

    @Query("SELECT d FROM Device d WHERE d.enabled = true AND d.type = :type AND (d.status <> :healthyStatus OR d.health <> :healthyHealth)")
    List<Device> findUnhealthyDevicesByType(
        @Param("type") Device.DeviceType type,
        @Param("healthyStatus") Device.DeviceStatus healthyStatus,
        @Param("healthyHealth") Device.DeviceHealth healthyHealth
    );
}
