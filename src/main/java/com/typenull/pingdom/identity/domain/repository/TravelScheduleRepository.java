package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelScheduleRepository extends JpaRepository<TravelSchedule, Long> {

    List<TravelSchedule> findAllByUser_IdOrderByStartDateAscIdAsc(Long userId);

    Optional<TravelSchedule> findByIdAndUser_Id(Long scheduleId, Long userId);

    boolean existsByUser_IdAndStateAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId,
            TravelScheduleState state,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
            SELECT CASE WHEN COUNT(schedule) > 0 THEN true ELSE false END
            FROM TravelSchedule schedule
            WHERE schedule.user.id = :userId
              AND schedule.state = :state
              AND schedule.startDate <= :endDate
              AND schedule.endDate >= :startDate
              AND (:excludedScheduleId IS NULL OR schedule.id <> :excludedScheduleId)
            """)
    boolean existsOverlappingSchedule(
            @Param("userId") Long userId,
            @Param("state") TravelScheduleState state,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedScheduleId") Long excludedScheduleId
    );

    @Modifying
    @Query("DELETE FROM TravelSchedule schedule WHERE schedule.user.id IN :userIds")
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
