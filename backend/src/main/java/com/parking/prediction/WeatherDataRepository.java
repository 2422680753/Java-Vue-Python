package com.parking.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherDataRepository extends JpaRepository<WeatherData, Long> {

    List<WeatherData> findByLocationAndRecordDateOrderByRecordHour(String location, LocalDate date);

    Optional<WeatherData> findByLocationAndRecordDateAndRecordHour(String location, LocalDate date, Integer hour);

    @Query("SELECT w FROM WeatherData w WHERE w.location = :location AND w.recordDate >= :startDate AND w.recordDate <= :endDate ORDER BY w.recordDate, w.recordHour")
    List<WeatherData> findByLocationAndDateRange(
        @Param("location") String location,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT w FROM WeatherData w WHERE w.location = :location ORDER BY w.recordDate DESC, w.recordHour DESC")
    List<WeatherData> findLatestByLocation(@Param("location") String location, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT AVG(w.temperature) FROM WeatherData w WHERE w.location = :location AND w.recordDate = :date")
    Optional<Double> getAverageTemperatureForDate(@Param("location") String location, @Param("date") LocalDate date);

    @Query("SELECT AVG(w.humidity) FROM WeatherData w WHERE w.location = :location AND w.recordDate = :date")
    Optional<Double> getAverageHumidityForDate(@Param("location") String location, @Param("date") LocalDate date);

    @Query("SELECT w.weatherCondition FROM WeatherData w WHERE w.location = :location AND w.recordDate = :date ORDER BY w.recordHour DESC")
    List<WeatherData.WeatherCondition> findWeatherConditionsForDate(
        @Param("location") String location, 
        @Param("date") LocalDate date
    );
}
