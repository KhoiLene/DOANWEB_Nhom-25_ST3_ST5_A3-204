package com.shopchain.repository;

import com.shopchain.entity.BeautyRoutineLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BeautyRoutineLogRepository extends JpaRepository<BeautyRoutineLog, Long> {
    List<BeautyRoutineLog> findAllByUserIdAndCareDate(Long userId, LocalDate careDate);

    Optional<BeautyRoutineLog> findByUserIdAndCareDateAndStepKey(Long userId, LocalDate careDate, String stepKey);

    @Query("select distinct log.careDate from BeautyRoutineLog log where log.user.id = :userId and log.completed = true and log.careDate <= :today order by log.careDate desc")
    List<LocalDate> findCompletedDates(@Param("userId") Long userId, @Param("today") LocalDate today);
}
