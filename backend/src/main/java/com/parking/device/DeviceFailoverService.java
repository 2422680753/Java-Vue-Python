package com.parking.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceFailoverService {

    private final DeviceRepository deviceRepository;
    private final DeviceAlertService alertService;

    private final Map<String, FailoverRecord> activeFailovers = new ConcurrentHashMap<>();
    private final Map<String, String> deviceMapping = new ConcurrentHashMap<>();

    @Transactional
    public boolean triggerFailover(Device failedDevice) {
        String failedDeviceId = failedDevice.getDeviceId();
        
        log.info("Triggering failover for failed device: {}", failedDeviceId);
        
        List<Device> backupDevices = findBackupDevices(failedDevice);
        
        if (backupDevices.isEmpty()) {
            log.error("No backup devices available for failed device: {}", failedDeviceId);
            alertService.sendDeviceCriticalAlert(failedDevice, 
                "设备故障且无备用设备可用！请立即检查设备: " + failedDevice.getName());
            return false;
        }
        
        Device selectedBackup = selectBestBackup(backupDevices, failedDevice);
        
        if (selectedBackup == null) {
            log.error("No suitable backup device found for: {}", failedDeviceId);
            return false;
        }
        
        performFailover(failedDevice, selectedBackup);
        
        return true;
    }

    private List<Device> findBackupDevices(Device failedDevice) {
        List<Device> candidates = new ArrayList<>();
        
        String backupDeviceId = failedDevice.getBackupDeviceId();
        if (backupDeviceId != null && !backupDeviceId.isEmpty()) {
            Optional<Device> explicitBackup = deviceRepository.findByDeviceId(backupDeviceId);
            if (explicitBackup.isPresent() && isEligible(explicitBackup.get())) {
                candidates.add(explicitBackup.get());
            }
        }
        
        Long parkingLotId = failedDevice.getParkingLotId();
        Device.DeviceType type = failedDevice.getType();
        String gateId = failedDevice.getGateId();
        
        if (gateId != null && !gateId.isEmpty()) {
            List<Device> sameGateDevices = deviceRepository.findByGateIdAndTypeOrderByPriority(gateId, type);
            for (Device dev : sameGateDevices) {
                if (!dev.getDeviceId().equals(failedDevice.getDeviceId()) && 
                    isEligible(dev) && !candidates.contains(dev)) {
                    candidates.add(dev);
                }
            }
        }
        
        if (parkingLotId != null) {
            List<Device> sameLotHealthy = deviceRepository.findHealthyDevicesByLotAndType(
                parkingLotId, type, Device.DeviceStatus.ONLINE, Device.DeviceHealth.HEALTHY);
            
            for (Device dev : sameLotHealthy) {
                if (!dev.getDeviceId().equals(failedDevice.getDeviceId()) && 
                    !candidates.contains(dev)) {
                    candidates.add(dev);
                }
            }
        }
        
        if (candidates.isEmpty()) {
            List<Device> allHealthy = deviceRepository.findHealthyDevicesByType(
                type, Device.DeviceStatus.ONLINE, Device.DeviceHealth.HEALTHY);
            
            for (Device dev : allHealthy) {
                if (!dev.getDeviceId().equals(failedDevice.getDeviceId()) && 
                    !candidates.contains(dev)) {
                    candidates.add(dev);
                }
            }
        }
        
        return candidates;
    }

    private boolean isEligible(Device device) {
        return device.getEnabled() && 
               Device.DeviceStatus.ONLINE.equals(device.getStatus()) && 
               Device.DeviceHealth.HEALTHY.equals(device.getHealth());
    }

    private Device selectBestBackup(List<Device> candidates, Device failedDevice) {
        if (candidates.isEmpty()) {
            return null;
        }
        
        candidates.sort((a, b) -> {
            int priorityCompare = b.getPriority() - a.getPriority();
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            
            boolean aSameLot = Objects.equals(a.getParkingLotId(), failedDevice.getParkingLotId());
            boolean bSameLot = Objects.equals(b.getParkingLotId(), failedDevice.getParkingLotId());
            if (aSameLot != bSameLot) {
                return aSameLot ? -1 : 1;
            }
            
            boolean aSameGate = Objects.equals(a.getGateId(), failedDevice.getGateId());
            boolean bSameGate = Objects.equals(b.getGateId(), failedDevice.getGateId());
            if (aSameGate != bSameGate) {
                return aSameGate ? -1 : 1;
            }
            
            LocalDateTime aHeartbeat = a.getLastHeartbeatTime();
            LocalDateTime bHeartbeat = b.getLastHeartbeatTime();
            if (aHeartbeat != null && bHeartbeat != null) {
                return bHeartbeat.compareTo(aHeartbeat);
            }
            
            return 0;
        });
        
        return candidates.get(0);
    }

    @Transactional
    protected void performFailover(Device failedDevice, Device backupDevice) {
        String failedDeviceId = failedDevice.getDeviceId();
        String backupDeviceId = backupDevice.getDeviceId();
        
        log.info("Performing failover: {} -> {}", failedDeviceId, backupDeviceId);
        
        FailoverRecord record = FailoverRecord.builder()
            .failedDeviceId(failedDeviceId)
            .backupDeviceId(backupDeviceId)
            .failoverTime(LocalDateTime.now())
            .failedDeviceName(failedDevice.getName())
            .backupDeviceName(backupDevice.getName())
            .failedDeviceType(failedDevice.getType())
            .build();
        
        activeFailovers.put(failedDeviceId, record);
        deviceMapping.put(failedDeviceId, backupDeviceId);
        
        if (failedDevice.getRole() == Device.DeviceRole.PRIMARY) {
            backupDevice.setRole(Device.DeviceRole.PRIMARY);
        }
        backupDevice.setRole(Device.DeviceRole.PRIMARY);
        deviceRepository.save(backupDevice);
        
        log.info("Failover completed: {} -> {}", failedDeviceId, backupDeviceId);
        
        String message = String.format(
            "设备故障已自动切换:\n故障设备: %s (%s)\n切换到: %s (%s)\n切换时间: %s",
            failedDevice.getName(), failedDeviceId,
            backupDevice.getName(), backupDeviceId,
            LocalDateTime.now()
        );
        
        alertService.sendDeviceCriticalAlert(failedDevice, message);
        alertService.sendDeviceInfoAlert(backupDevice, "设备已接管来自 " + failedDevice.getName() + " 的流量");
    }

    @Transactional
    public boolean recoverDevice(String failedDeviceId) {
        log.info("Attempting to recover device: {}", failedDeviceId);
        
        Optional<Device> failedDeviceOpt = deviceRepository.findByDeviceId(failedDeviceId);
        
        if (failedDeviceOpt.isEmpty()) {
            log.error("Device not found for recovery: {}", failedDeviceId);
            return false;
        }
        
        Device failedDevice = failedDeviceOpt.get();
        
        if (!activeFailovers.containsKey(failedDeviceId)) {
            log.warn("No active failover for device: {}", failedDeviceId);
            return false;
        }
        
        if (!isEligible(failedDevice)) {
            log.warn("Device {} not yet eligible for recovery - status: {}, health: {}", 
                failedDeviceId, failedDevice.getStatus(), failedDevice.getHealth());
            return false;
        }
        
        return performRecovery(failedDevice);
    }

    @Transactional
    protected boolean performRecovery(Device device) {
        String deviceId = device.getDeviceId();
        
        log.info("Performing recovery for device: {}", deviceId);
        
        FailoverRecord record = activeFailovers.remove(deviceId);
        
        if (record == null) {
            log.warn("No failover record found for recovery: {}", deviceId);
            return false;
        }
        
        String backupDeviceId = record.getBackupDeviceId();
        
        deviceMapping.remove(deviceId);
        
        Optional<Device> backupDeviceOpt = deviceRepository.findByDeviceId(backupDeviceId);
        if (backupDeviceOpt.isPresent()) {
            Device backupDevice = backupDeviceOpt.get();
            if (Device.DeviceRole.BACKUP.equals(backupDevice.getRole())) {
                backupDevice.setRole(Device.DeviceRole.BACKUP);
            }
            deviceRepository.save(backupDevice);
        }
        
        if (device.getErrorCount() > 0) {
            device.clearErrors();
        }
        device.setRole(Device.DeviceRole.PRIMARY);
        deviceRepository.save(device);
        
        log.info("Device recovery completed: {}", deviceId);
        
        alertService.sendDeviceRecoveredAlert(device, 
            "设备已恢复正常，故障切换已回滚。" +
            "\n之前的备份设备: " + record.getBackupDeviceName() +
            "\n恢复时间: " + LocalDateTime.now());
        
        return true;
    }

    public String getActiveDeviceFor(String logicalDeviceId) {
        String mappedDevice = deviceMapping.get(logicalDeviceId);
        if (mappedDevice != null) {
            return mappedDevice;
        }
        return logicalDeviceId;
    }

    public boolean isInFailover(String deviceId) {
        return activeFailovers.containsKey(deviceId);
    }

    public FailoverRecord getFailoverRecord(String deviceId) {
        return activeFailovers.get(deviceId);
    }

    public Map<String, Object> getFailoverStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("activeFailovers", activeFailovers.size());
        stats.put("deviceMappings", deviceMapping.size());
        
        List<Map<String, Object>> failoverDetails = new ArrayList<>();
        for (Map.Entry<String, FailoverRecord> entry : activeFailovers.entrySet()) {
            FailoverRecord record = entry.getValue();
            Map<String, Object> detail = new HashMap<>();
            detail.put("failedDeviceId", record.getFailedDeviceId());
            detail.put("failedDeviceName", record.getFailedDeviceName());
            detail.put("failedDeviceType", record.getFailedDeviceType());
            detail.put("backupDeviceId", record.getBackupDeviceId());
            detail.put("backupDeviceName", record.getBackupDeviceName());
            detail.put("failoverTime", record.getFailoverTime());
            failoverDetails.add(detail);
        }
        
        stats.put("failoverDetails", failoverDetails);
        
        return stats;
    }

    public void checkRecoverableDevices() {
        Set<String> failedDeviceIds = new HashSet<>(activeFailovers.keySet());
        
        for (String deviceId : failedDeviceIds) {
            try {
                Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
                if (deviceOpt.isPresent()) {
                    Device device = deviceOpt.get();
                    
                    if (isEligible(device)) {
                        log.info("Device {} appears to be healthy, checking if should recover", deviceId);
                        
                        if (device.getErrorCount() == 0 || device.getLastHeartbeatTime() != null) {
                            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
                            if (device.getLastHeartbeatTime() != null && 
                                device.getLastHeartbeatTime().isAfter(fiveMinutesAgo)) {
                                log.info("Device {} has been healthy for 5+ minutes, auto-recovering", deviceId);
                                recoverDevice(deviceId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error checking recoverable device: {}", deviceId, e);
            }
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class FailoverRecord {
        private String failedDeviceId;
        private String failedDeviceName;
        private Device.DeviceType failedDeviceType;
        private String backupDeviceId;
        private String backupDeviceName;
        private LocalDateTime failoverTime;
        private String reason;
    }
}
