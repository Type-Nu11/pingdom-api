package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TravelScheduleService {

    private final UserRepository userRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final Clock clock;

    @Transactional
    public TravelSchedule create(Long userId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate);
        TravelSchedule schedule = TravelSchedule.create(findUser(userId), startDate, endDate);
        return travelScheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public List<TravelSchedule> getSchedules(Long userId) {
        findUser(userId);
        return travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(userId);
    }

    @Transactional
    public TravelSchedule update(Long userId, Long scheduleId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate);
        TravelSchedule schedule = findSchedule(userId, scheduleId);
        try {
            schedule.updatePeriod(startDate, endDate);
        } catch (IllegalStateException exception) {
            throw new UsersException(UsersErrorCode.TRAVEL_SCHEDULE_NOT_EDITABLE);
        }
        return flushSchedule(schedule);
    }

    @Transactional
    public TravelSchedule cancel(Long userId, Long scheduleId) {
        TravelSchedule schedule = findSchedule(userId, scheduleId);
        schedule.cancel();
        return flushSchedule(schedule);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }

    private TravelSchedule findSchedule(Long userId, Long scheduleId) {
        return travelScheduleRepository.findByIdAndUser_Id(scheduleId, userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.TRAVEL_SCHEDULE_NOT_FOUND));
    }

    private TravelSchedule flushSchedule(TravelSchedule schedule) {
        try {
            return travelScheduleRepository.saveAndFlush(schedule);
        } catch (OptimisticLockingFailureException exception) {
            throw new UsersException(UsersErrorCode.TRAVEL_SCHEDULE_CONCURRENT_MODIFICATION);
        }
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new UsersException(UsersErrorCode.INVALID_TRAVEL_SCHEDULE_PERIOD);
        }
    }
}
