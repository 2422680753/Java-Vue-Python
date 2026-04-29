package com.parking.controller;

import com.parking.prediction.*;
import com.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/traffic-prediction")
@RequiredArgsConstructor
@Slf4j
public class TrafficPredictionController {

    private final TrafficPredictionService predictionService;
    private final HolidayRepository holidayRepository;
    private final WeatherDataRepository weatherRepository;
    private final SpecialEventRepository eventRepository;
    private final ParkingLotRepository parkingLotRepository;

    @GetMapping("/{parkingLotId}/now")
    public ResponseEntity<TrafficPredictionService.TrafficPredictionResult> predictNow(
            @PathVariable Long parkingLotId) {
        
        LocalDateTime now = LocalDateTime.now();
        TrafficPredictionService.TrafficPredictionResult result = 
            predictionService.predictTraffic(parkingLotId, now);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{parkingLotId}/at")
    public ResponseEntity<TrafficPredictionService.TrafficPredictionResult> predictAt(
            @PathVariable Long parkingLotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {
        
        TrafficPredictionService.TrafficPredictionResult result = 
            predictionService.predictTraffic(parkingLotId, targetTime);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{parkingLotId}/next/{hours}")
    public ResponseEntity<List<TrafficPredictionService.TrafficPredictionResult>> predictNextHours(
            @PathVariable Long parkingLotId,
            @PathVariable int hours) {
        
        List<TrafficPredictionService.TrafficPredictionResult> results = 
            predictionService.predictNextHours(parkingLotId, hours);
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{parkingLotId}/day")
    public ResponseEntity<List<TrafficPredictionService.TrafficPredictionResult>> predictDay(
            @PathVariable Long parkingLotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        if (date == null) {
            date = LocalDate.now();
        }
        
        List<TrafficPredictionService.TrafficPredictionResult> results = 
            predictionService.predictTrafficForDay(parkingLotId, date);
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{parkingLotId}/accuracy")
    public ResponseEntity<Map<String, Object>> getAccuracyStats(@PathVariable Long parkingLotId) {
        Map<String, Object> stats = predictionService.getPredictionAccuracyStats(parkingLotId);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/holidays")
    public ResponseEntity<Holiday> createHoliday(@RequestBody Holiday holiday) {
        if (holidayRepository.existsByHolidayDate(holiday.getHolidayDate())) {
            return ResponseEntity.badRequest().build();
        }
        
        Holiday saved = holidayRepository.save(holiday);
        log.info("Created holiday: {} on {}", saved.getName(), saved.getHolidayDate());
        
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/holidays")
    public ResponseEntity<List<Holiday>> getHolidays(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Holiday.HolidayType type) {
        
        List<Holiday> holidays;
        
        if (startDate != null && endDate != null) {
            holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(startDate, endDate);
        } else if (type != null) {
            holidays = holidayRepository.findByTypeOrderByHolidayDate(type);
        } else {
            holidays = holidayRepository.findAll();
        }
        
        return ResponseEntity.ok(holidays);
    }

    @GetMapping("/holidays/{date}")
    public ResponseEntity<Holiday> getHolidayByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Optional<Holiday> holiday = holidayRepository.findByHolidayDate(date);
        return holiday.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/holidays/{date}")
    public ResponseEntity<Void> deleteHoliday(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        if (!holidayRepository.existsByHolidayDate(date)) {
            return ResponseEntity.notFound().build();
        }
        
        holidayRepository.deleteByHolidayDate(date);
        log.info("Deleted holiday on date: {}", date);
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/holidays/init/{year}")
    public ResponseEntity<Map<String, Object>> initializeDefaultHolidays(@PathVariable int year) {
        predictionService.initializeDefaultHolidays(year);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("year", year);
        response.put("message", "Default holidays initialized for year " + year);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/weather")
    public ResponseEntity<WeatherData> createWeatherData(@RequestBody WeatherData weatherData) {
        WeatherData saved = weatherRepository.save(weatherData);
        log.info("Created weather data for: {} at {}:00", 
            saved.getLocation(), saved.getRecordHour());
        
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/weather/{location}")
    public ResponseEntity<List<WeatherData>> getWeatherData(
            @PathVariable String location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<WeatherData> data;
        
        if (date != null) {
            data = weatherRepository.findByLocationAndRecordDateOrderByRecordHour(location, date);
        } else if (startDate != null && endDate != null) {
            data = weatherRepository.findByLocationAndDateRange(location, startDate, endDate);
        } else {
            data = weatherRepository.findLatestByLocation(location, 
                org.springframework.data.domain.Pageable.ofSize(24));
        }
        
        return ResponseEntity.ok(data);
    }

    @PostMapping("/events")
    public ResponseEntity<SpecialEvent> createSpecialEvent(@RequestBody SpecialEvent event) {
        SpecialEvent saved = eventRepository.save(event);
        log.info("Created special event: {} from {} to {}", 
            saved.getEventName(), saved.getStartTime(), saved.getEndTime());
        
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/events")
    public ResponseEntity<List<SpecialEvent>> getEvents(
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) Boolean active) {
        
        List<SpecialEvent> events;
        
        if (parkingLotId != null && Boolean.TRUE.equals(active)) {
            events = eventRepository.findByParkingLotIdAndIsActiveTrueOrderByStartTime(parkingLotId);
        } else if (Boolean.TRUE.equals(active)) {
            events = eventRepository.findByIsActiveTrueOrderByStartTime();
        } else if (parkingLotId != null) {
            events = eventRepository.findByParkingLotIdAndIsActiveTrueOrderByStartTime(parkingLotId);
        } else {
            events = eventRepository.findAll();
        }
        
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<SpecialEvent> getEvent(@PathVariable Long eventId) {
        return eventRepository.findById(eventId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<SpecialEvent> updateEvent(
            @PathVariable Long eventId,
            @RequestBody SpecialEvent event) {
        
        Optional<SpecialEvent> existingOpt = eventRepository.findById(eventId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        SpecialEvent existing = existingOpt.get();
        
        if (event.getEventName() != null) existing.setEventName(event.getEventName());
        if (event.getEventType() != null) existing.setEventType(event.getEventType());
        if (event.getLocation() != null) existing.setLocation(event.getLocation());
        if (event.getStartTime() != null) existing.setStartTime(event.getStartTime());
        if (event.getEndTime() != null) existing.setEndTime(event.getEndTime());
        if (event.getExpectedVisitors() != null) existing.setExpectedVisitors(event.getExpectedVisitors());
        if (event.getParkingLotId() != null) existing.setParkingLotId(event.getParkingLotId());
        if (event.getTrafficFactor() != null) existing.setTrafficFactor(event.getTrafficFactor());
        if (event.getIsActive() != null) existing.setIsActive(event.getIsActive());
        if (event.getDescription() != null) existing.setDescription(event.getDescription());
        
        SpecialEvent saved = eventRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            return ResponseEntity.notFound().build();
        }
        
        eventRepository.deleteById(eventId);
        log.info("Deleted special event: {}", eventId);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/factors/explain")
    public ResponseEntity<Map<String, Object>> explainFactors(
            @RequestParam Long parkingLotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {
        
        TrafficPredictionService.TrafficPredictionResult result = 
            predictionService.predictTraffic(parkingLotId, targetTime);
        
        Map<String, Object> explanation = new HashMap<>();
        explanation.put("prediction", result);
        explanation.put("targetTime", targetTime);
        explanation.put("parkingLotId", parkingLotId);
        
        Map<String, Object> factorsBreakdown = new HashMap<>();
        
        factorsBreakdown.put("timeOfDay", Map.of(
            "factor", result.getTimeOfDayFactor(),
            "description", "基于历史时段的车流系数"
        ));
        
        factorsBreakdown.put("holiday", Map.of(
            "factor", result.getHolidayFactor(),
            "isHoliday", result.isHoliday(),
            "holidayName", result.getHolidayName() != null ? result.getHolidayName() : "无"
        ));
        
        factorsBreakdown.put("weather", Map.of(
            "factor", result.getWeatherFactor(),
            "condition", result.getWeatherCondition() != null ? result.getWeatherCondition() : "未知"
        ));
        
        factorsBreakdown.put("specialEvent", Map.of(
            "factor", result.getEventFactor(),
            "hasEvent", result.isHasSpecialEvent(),
            "eventName", result.getEventName() != null ? result.getEventName() : "无"
        ));
        
        factorsBreakdown.put("combined", Map.of(
            "factor", result.getCombinedFactor(),
            "description", "综合系数（取主要影响因素，其他因素按30%权重叠加）"
        ));
        
        explanation.put("factorsBreakdown", factorsBreakdown);
        explanation.put("factorsApplied", result.getFactorsApplied());
        
        return ResponseEntity.ok(explanation);
    }

    @PostMapping("/cache/refresh")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        predictionService.refreshHistoricalCache();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Historical traffic cache refreshed");
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }
}
