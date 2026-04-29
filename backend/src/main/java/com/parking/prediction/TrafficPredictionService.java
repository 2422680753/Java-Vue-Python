package com.parking.prediction;

import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingRecord;
import com.parking.repository.ParkingLotRepository;
import com.parking.repository.ParkingRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrafficPredictionService {

    private final HolidayRepository holidayRepository;
    private final WeatherDataRepository weatherRepository;
    private final SpecialEventRepository eventRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final ParkingRecordRepository recordRepository;

    private static final int PREDICTION_HOURS_AHEAD = 24;
    private static final int HISTORY_DAYS = 30;
    private static final double MAX_ALLOWED_ERROR = 0.10;

    private final Map<Long, Map<Integer, HourlyTrafficStats>> historicalTrafficCache = new ConcurrentHashMap<>();
    private final Map<Long, List<TrafficPredictionResult>> recentPredictions = new ConcurrentHashMap<>();

    @Data
    public static class HourlyTrafficStats {
        private int hour;
        private double averageTraffic;
        private double medianTraffic;
        private double minTraffic;
        private double maxTraffic;
        private double variance;
        private double standardDeviation;
        private int sampleCount;
        private boolean isPeakHour;
        private Map<DayOfWeek, Double> dayOfWeekTraffic = new EnumMap<>(DayOfWeek.class);
    }

    @lombok.Data
    @lombok.Builder
    public static class TrafficPredictionResult {
        private Long parkingLotId;
        private String parkingLotName;
        private LocalDateTime predictionTime;
        private LocalDateTime targetTime;
        private int predictedTraffic;
        private double confidence;
        private double predictionError;
        private int actualTraffic;
        private String weatherCondition;
        private boolean isHoliday;
        private String holidayName;
        private boolean hasSpecialEvent;
        private String eventName;
        private double holidayFactor;
        private double weatherFactor;
        private double eventFactor;
        private double timeOfDayFactor;
        private double combinedFactor;
        private String modelVersion;
        private List<String> factorsApplied;
    }

    @lombok.Data
    @lombok.Builder
    public static class PredictionContext {
        private Long parkingLotId;
        private LocalDateTime targetTime;
        private boolean isHoliday;
        private Holiday holiday;
        private WeatherData.WeatherCondition weatherCondition;
        private double weatherFactor;
        private List<SpecialEvent> activeEvents;
        private double eventFactor;
        private double timeOfDayFactor;
        private double historicalBaseTraffic;
        private HourlyTrafficStats stats;
    }

    @Transactional
    public TrafficPredictionResult predictTraffic(Long parkingLotId, LocalDateTime targetTime) {
        log.debug("Predicting traffic for parking lot {} at {}", parkingLotId, targetTime);
        
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
            .orElseThrow(() -> new IllegalArgumentException("Parking lot not found: " + parkingLotId));
        
        PredictionContext context = buildPredictionContext(parkingLotId, targetTime);
        
        HourlyTrafficStats stats = getOrCalculateHourlyStats(parkingLotId, targetTime.getHour(), targetTime);
        context.setStats(stats);
        
        double baseTraffic = stats.getAverageTraffic();
        context.setHistoricalBaseTraffic(baseTraffic);
        
        double combinedFactor = calculateCombinedFactor(context);
        
        int predictedTraffic = (int) Math.round(baseTraffic * combinedFactor);
        
        double confidence = calculateConfidence(context, stats);
        
        TrafficPredictionResult result = TrafficPredictionResult.builder()
            .parkingLotId(parkingLotId)
            .parkingLotName(parkingLot.getName())
            .predictionTime(LocalDateTime.now())
            .targetTime(targetTime)
            .predictedTraffic(predictedTraffic)
            .confidence(confidence)
            .weatherCondition(context.getWeatherCondition() != null ? 
                context.getWeatherCondition().getLabel() : "UNKNOWN")
            .isHoliday(context.isHoliday())
            .holidayName(context.getHoliday() != null ? context.getHoliday().getName() : null)
            .hasSpecialEvent(!context.getActiveEvents().isEmpty())
            .eventName(context.getActiveEvents().isEmpty() ? null : 
                context.getActiveEvents().get(0).getEventName())
            .holidayFactor(context.isHoliday() ? 
                (context.getHoliday() != null ? context.getHoliday().getTrafficFactor() : 1.5) : 1.0)
            .weatherFactor(context.getWeatherFactor())
            .eventFactor(context.getEventFactor())
            .timeOfDayFactor(context.getTimeOfDayFactor())
            .combinedFactor(combinedFactor)
            .modelVersion("2.0.0-MultiFactor")
            .factorsApplied(buildFactorsList(context))
            .build();
        
        cachePrediction(parkingLotId, result);
        
        log.info("Traffic prediction for lot {} at {}: {}, confidence: {:.1%}, factors: holiday={}, weather={}, event={}, combined={}",
            parkingLotId, targetTime, predictedTraffic, confidence,
            result.getHolidayFactor(), result.getWeatherFactor(), 
            result.getEventFactor(), result.getCombinedFactor());
        
        return result;
    }

    private PredictionContext buildPredictionContext(Long parkingLotId, LocalDateTime targetTime) {
        LocalDate targetDate = targetTime.toLocalDate();
        int targetHour = targetTime.getHour();
        
        PredictionContext.PredictionContextBuilder builder = PredictionContext.builder()
            .parkingLotId(parkingLotId)
            .targetTime(targetTime)
            .isHoliday(false)
            .eventFactor(1.0)
            .activeEvents(Collections.emptyList());
        
        Optional<Holiday> holidayOpt = holidayRepository.findByHolidayDate(targetDate);
        holidayOpt.ifPresent(holiday -> {
            builder.isHoliday(true);
            builder.holiday(holiday);
        });
        
        DayOfWeek dayOfWeek = targetTime.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        if (isWeekend && holidayOpt.isEmpty()) {
            builder.isHoliday(true);
        }
        
        Optional<WeatherData> weatherOpt = weatherRepository.findByLocationAndRecordDateAndRecordHour(
            "DEFAULT", targetDate, targetHour);
        
        if (weatherOpt.isPresent()) {
            WeatherData weather = weatherOpt.get();
            builder.weatherCondition(weather.getWeatherCondition());
            builder.weatherFactor(weather.getTrafficImpactFactor());
        } else {
            builder.weatherCondition(WeatherData.WeatherCondition.CLEAR);
            builder.weatherFactor(1.0);
        }
        
        List<SpecialEvent> activeEvents = eventRepository.findActiveEventsAtTime(parkingLotId, targetTime);
        if (!activeEvents.isEmpty()) {
            builder.activeEvents(activeEvents);
            
            double maxEventFactor = 1.0;
            for (SpecialEvent event : activeEvents) {
                double eventImpact = event.getTrafficImpactAt(targetTime);
                maxEventFactor = Math.max(maxEventFactor, eventImpact);
            }
            builder.eventFactor(maxEventFactor);
        }
        
        double timeOfDayFactor = calculateTimeOfDayFactor(targetTime);
        builder.timeOfDayFactor(timeOfDayFactor);
        
        return builder.build();
    }

    private double calculateTimeOfDayFactor(LocalDateTime time) {
        int hour = time.getHour();
        DayOfWeek dayOfWeek = time.getDayOfWeek();
        boolean isWeekday = dayOfWeek.getValue() >= 1 && dayOfWeek.getValue() <= 5;
        
        if (isWeekday) {
            if (hour >= 7 && hour < 9) {
                return 1.8;
            } else if (hour >= 9 && hour < 12) {
                return 1.2;
            } else if (hour >= 12 && hour < 14) {
                return 1.5;
            } else if (hour >= 14 && hour < 17) {
                return 1.1;
            } else if (hour >= 17 && hour < 19) {
                return 1.9;
            } else if (hour >= 19 && hour < 22) {
                return 1.3;
            } else {
                return 0.6;
            }
        } else {
            if (hour >= 10 && hour < 12) {
                return 1.4;
            } else if (hour >= 12 && hour < 14) {
                return 1.6;
            } else if (hour >= 14 && hour < 18) {
                return 1.5;
            } else if (hour >= 18 && hour < 22) {
                return 1.4;
            } else if (hour >= 22 || hour < 6) {
                return 0.5;
            } else {
                return 0.8;
            }
        }
    }

    private double calculateCombinedFactor(PredictionContext context) {
        double holidayFactor = context.isHoliday() ? 
            (context.getHoliday() != null ? context.getHoliday().getTrafficFactor() : 1.5) : 1.0;
        
        double weatherFactor = context.getWeatherFactor();
        double eventFactor = context.getEventFactor();
        double timeOfDayFactor = context.getTimeOfDayFactor();
        
        List<Double> factors = new ArrayList<>();
        factors.add(holidayFactor);
        factors.add(weatherFactor);
        factors.add(eventFactor);
        
        double maxExternalFactor = factors.stream()
            .max(Double::compareTo)
            .orElse(1.0);
        
        double otherFactorsProduct = factors.stream()
            .mapToDouble(f -> f)
            .filter(f -> f != maxExternalFactor)
            .reduce(1.0, (a, b) -> a + (b - 1.0) * 0.3);
        
        double combinedFactor = maxExternalFactor * otherFactorsProduct * timeOfDayFactor;
        
        return Math.max(0.3, Math.min(5.0, combinedFactor));
    }

    private double calculateConfidence(PredictionContext context, HourlyTrafficStats stats) {
        double confidence = 0.6;
        
        if (stats.getSampleCount() >= 7) {
            confidence += 0.15;
        } else if (stats.getSampleCount() >= 3) {
            confidence += 0.08;
        }
        
        double cv = stats.getSampleCount() > 1 ? 
            stats.getStandardDeviation() / Math.max(1, stats.getAverageTraffic()) : 0;
        
        if (cv < 0.2) {
            confidence += 0.1;
        } else if (cv < 0.4) {
            confidence += 0.05;
        } else {
            confidence -= 0.1;
        }
        
        if (context.isHoliday()) {
            confidence -= 0.05;
        }
        
        if (context.getEventFactor() > 1.0) {
            confidence -= 0.1;
        }
        
        if (context.getWeatherFactor() > 1.2) {
            confidence -= 0.05;
        }
        
        return Math.min(0.99, Math.max(0.3, confidence));
    }

    private List<String> buildFactorsList(PredictionContext context) {
        List<String> factors = new ArrayList<>();
        
        if (context.isHoliday() && context.getHoliday() != null) {
            factors.add("节假日: " + context.getHoliday().getName() + 
                " (系数: " + context.getHoliday().getTrafficFactor() + ")");
        } else if (context.isHoliday()) {
            factors.add("周末 (系数: 1.5)");
        }
        
        if (context.getWeatherFactor() != 1.0) {
            factors.add("天气: " + context.getWeatherCondition() + 
                " (系数: " + context.getWeatherFactor() + ")");
        }
        
        if (context.getEventFactor() > 1.0 && !context.getActiveEvents().isEmpty()) {
            factors.add("特殊活动: " + context.getActiveEvents().stream()
                .map(SpecialEvent::getEventName)
                .collect(Collectors.joining(", ")) +
                " (系数: " + context.getEventFactor() + ")");
        }
        
        factors.add("时段系数: " + String.format("%.1f", context.getTimeOfDayFactor()));
        
        return factors;
    }

    private HourlyTrafficStats getOrCalculateHourlyStats(Long parkingLotId, int hour, LocalDateTime targetTime) {
        Map<Integer, HourlyTrafficStats> lotStats = historicalTrafficCache.computeIfAbsent(
            parkingLotId, k -> new ConcurrentHashMap<>());
        
        return lotStats.computeIfAbsent(hour, h -> calculateHourlyStats(parkingLotId, hour, targetTime));
    }

    private HourlyTrafficStats calculateHourlyStats(Long parkingLotId, int hour, LocalDateTime targetTime) {
        LocalDateTime endTime = targetTime;
        LocalDateTime startTime = endTime.minusDays(HISTORY_DAYS);
        
        List<ParkingRecord> records = recordRepository.findEntryRecordsForLotInTimeRange(
            parkingLotId, startTime, endTime);
        
        List<Integer> hourlyCounts = new ArrayList<>();
        Map<DayOfWeek, List<Integer>> dayOfWeekCounts = new EnumMap<>(DayOfWeek.class);
        
        for (ParkingRecord record : records) {
            if (record.getEntryTime() != null) {
                LocalDateTime entryTime = record.getEntryTime();
                if (entryTime.getHour() == hour) {
                    hourlyCounts.add(1);
                    
                    DayOfWeek dow = entryTime.getDayOfWeek();
                    dayOfWeekCounts.computeIfAbsent(dow, k -> new ArrayList<>()).add(1);
                }
            }
        }
        
        Map<LocalDate, Integer> dailyCounts = records.stream()
            .filter(r -> r.getEntryTime() != null && r.getEntryTime().getHour() == hour)
            .collect(Collectors.groupingBy(
                r -> r.getEntryTime().toLocalDate(),
                Collectors.summingInt(r -> 1)
            ));
        
        List<Integer> counts = new ArrayList<>(dailyCounts.values());
        if (counts.isEmpty()) {
            counts.add(50);
        }
        
        HourlyTrafficStats stats = new HourlyTrafficStats();
        stats.setHour(hour);
        stats.setAverageTraffic(counts.stream().mapToInt(Integer::intValue).average().orElse(50.0));
        stats.setMedianTraffic(calculateMedian(counts));
        stats.setMinTraffic(counts.stream().mapToInt(Integer::intValue).min().orElse(0));
        stats.setMaxTraffic(counts.stream().mapToInt(Integer::intValue).max().orElse(100));
        stats.setSampleCount(counts.size());
        
        double mean = stats.getAverageTraffic();
        double variance = counts.stream()
            .mapToDouble(c -> Math.pow(c - mean, 2))
            .average()
            .orElse(0.0);
        stats.setVariance(variance);
        stats.setStandardDeviation(Math.sqrt(variance));
        
        for (DayOfWeek dow : DayOfWeek.values()) {
            List<Integer> dowCounts = dayOfWeekCounts.getOrDefault(dow, Collections.emptyList());
            double avg = dowCounts.stream().mapToInt(Integer::intValue).average().orElse(mean);
            stats.getDayOfWeekTraffic().put(dow, avg);
        }
        
        stats.setPeakHour(stats.getAverageTraffic() > 70);
        
        return stats;
    }

    private double calculateMedian(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private void cachePrediction(Long parkingLotId, TrafficPredictionResult result) {
        recentPredictions.computeIfAbsent(parkingLotId, k -> new ArrayList<>());
        
        List<TrafficPredictionResult> predictions = recentPredictions.get(parkingLotId);
        predictions.add(result);
        
        while (predictions.size() > 100) {
            predictions.remove(0);
        }
    }

    public List<TrafficPredictionResult> predictTrafficForDay(Long parkingLotId, LocalDate date) {
        List<TrafficPredictionResult> results = new ArrayList<>();
        
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime targetTime = LocalDateTime.of(date, LocalTime.of(hour, 0));
            results.add(predictTraffic(parkingLotId, targetTime));
        }
        
        return results;
    }

    public List<TrafficPredictionResult> predictNextHours(Long parkingLotId, int hours) {
        List<TrafficPredictionResult> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < hours; i++) {
            LocalDateTime targetTime = now.plusHours(i).truncatedTo(ChronoUnit.HOURS);
            results.add(predictTraffic(parkingLotId, targetTime));
        }
        
        return results;
    }

    @Scheduled(cron = "0 0 */4 * * ?")
    public void refreshHistoricalCache() {
        log.info("Refreshing historical traffic cache...");
        
        List<ParkingLot> lots = parkingLotRepository.findAll();
        
        for (ParkingLot lot : lots) {
            historicalTrafficCache.remove(lot.getId());
            log.debug("Cleared cache for parking lot: {}", lot.getId());
        }
        
        log.info("Historical traffic cache refreshed");
    }

    @Transactional
    public void initializeDefaultHolidays(int year) {
        log.info("Initializing default holidays for year: {}", year);
        
        Map<LocalDate, String[]> defaultHolidays = new HashMap<>();
        
        defaultHolidays.put(LocalDate.of(year, 1, 1), 
            new String[]{"元旦", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 2, 10), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "2.0"});
        defaultHolidays.put(LocalDate.of(year, 2, 11), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "2.0"});
        defaultHolidays.put(LocalDate.of(year, 2, 12), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "2.0"});
        defaultHolidays.put(LocalDate.of(year, 2, 13), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "2.0"});
        defaultHolidays.put(LocalDate.of(year, 2, 14), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "1.8"});
        defaultHolidays.put(LocalDate.of(year, 2, 15), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 2, 16), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 2, 17), 
            new String[]{"春节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 4, 4), 
            new String[]{"清明节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 4, 5), 
            new String[]{"清明节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 4, 6), 
            new String[]{"清明节", "NATIONAL_HOLIDAY", "1.2"});
        defaultHolidays.put(LocalDate.of(year, 5, 1), 
            new String[]{"劳动节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 5, 2), 
            new String[]{"劳动节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 5, 3), 
            new String[]{"劳动节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 5, 4), 
            new String[]{"劳动节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 5, 5), 
            new String[]{"劳动节", "NATIONAL_HOLIDAY", "1.2"});
        defaultHolidays.put(LocalDate.of(year, 6, 8), 
            new String[]{"端午节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 6, 9), 
            new String[]{"端午节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 6, 10), 
            new String[]{"端午节", "NATIONAL_HOLIDAY", "1.2"});
        defaultHolidays.put(LocalDate.of(year, 9, 15), 
            new String[]{"中秋节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 9, 16), 
            new String[]{"中秋节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 9, 17), 
            new String[]{"中秋节", "NATIONAL_HOLIDAY", "1.2"});
        defaultHolidays.put(LocalDate.of(year, 10, 1), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.8"});
        defaultHolidays.put(LocalDate.of(year, 10, 2), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.8"});
        defaultHolidays.put(LocalDate.of(year, 10, 3), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.8"});
        defaultHolidays.put(LocalDate.of(year, 10, 4), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 10, 5), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.5"});
        defaultHolidays.put(LocalDate.of(year, 10, 6), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.3"});
        defaultHolidays.put(LocalDate.of(year, 10, 7), 
            new String[]{"国庆节", "NATIONAL_HOLIDAY", "1.3"});
        
        int count = 0;
        for (Map.Entry<LocalDate, String[]> entry : defaultHolidays.entrySet()) {
            LocalDate date = entry.getKey();
            String[] data = entry.getValue();
            
            if (!holidayRepository.existsByHolidayDate(date)) {
                Holiday holiday = new Holiday();
                holiday.setHolidayDate(date);
                holiday.setName(data[0]);
                holiday.setType(Holiday.HolidayType.valueOf(data[1]));
                holiday.setIsPeakDay(true);
                holiday.setTrafficFactor(Double.parseDouble(data[2]));
                holiday.setDescription("自动初始化的" + data[0] + "假期数据");
                holidayRepository.save(holiday);
                count++;
            }
        }
        
        log.info("Initialized {} default holidays for year {}", count, year);
    }

    public Map<String, Object> getPredictionAccuracyStats(Long parkingLotId) {
        Map<String, Object> stats = new HashMap<>();
        
        List<TrafficPredictionResult> predictions = recentPredictions.getOrDefault(
            parkingLotId, Collections.emptyList());
        
        if (predictions.isEmpty()) {
            stats.put("totalPredictions", 0);
            stats.put("message", "No predictions found for analysis");
            return stats;
        }
        
        long totalPredictions = predictions.size();
        long accurateCount = predictions.stream()
            .filter(p -> p.getPredictionError() <= MAX_ALLOWED_ERROR)
            .count();
        
        double averageError = predictions.stream()
            .mapToDouble(TrafficPredictionResult::getPredictionError)
            .average()
            .orElse(0.0);
        
        double accuracyRate = (double) accurateCount / totalPredictions;
        
        stats.put("totalPredictions", totalPredictions);
        stats.put("accurateCount", accurateCount);
        stats.put("accuracyRate", accuracyRate);
        stats.put("averageErrorPercent", averageError * 100);
        stats.put("maxAllowedErrorPercent", MAX_ALLOWED_ERROR * 100);
        stats.put("meetsTarget", accuracyRate >= 0.90);
        
        Map<String, Double> factorImpacts = new HashMap<>();
        factorImpacts.put("holidayFactor", predictions.stream()
            .mapToDouble(TrafficPredictionResult::getHolidayFactor)
            .average().orElse(1.0));
        factorImpacts.put("weatherFactor", predictions.stream()
            .mapToDouble(TrafficPredictionResult::getWeatherFactor)
            .average().orElse(1.0));
        factorImpacts.put("eventFactor", predictions.stream()
            .mapToDouble(TrafficPredictionResult::getEventFactor)
            .average().orElse(1.0));
        factorImpacts.put("timeOfDayFactor", predictions.stream()
            .mapToDouble(TrafficPredictionResult::getTimeOfDayFactor)
            .average().orElse(1.0));
        
        stats.put("averageFactors", factorImpacts);
        
        return stats;
    }
}
