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
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByHolidayDate(LocalDate date);

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate startDate, LocalDate endDate);

    List<Holiday> findByTypeOrderByHolidayDate(Holiday.HolidayType type);

    @Query("SELECT h FROM Holiday h WHERE h.holidayDate >= :startDate AND h.holidayDate <= :endDate AND h.isPeakDay = true")
    List<Holiday> findPeakDaysBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT AVG(h.trafficFactor) FROM Holiday h WHERE h.holidayDate = :date AND h.isPeakDay = true")
    Optional<Double> getAverageTrafficFactorForDate(@Param("date") LocalDate date);

    boolean existsByHolidayDate(LocalDate date);

    void deleteByHolidayDate(LocalDate date);
}
