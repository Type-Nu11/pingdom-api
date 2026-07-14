package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
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

    @Modifying
    @Query("DELETE FROM TravelSchedule schedule WHERE schedule.user.id IN :userIds")
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
