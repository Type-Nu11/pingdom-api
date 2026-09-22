package com.typenull.pingdom.identity.application.service.travel;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 소유 여행 일정의 생성·변경·취소와 기간 중복 검사를 조정합니다.
 * 날짜 양 끝을 포함해 중복을 확인하고 수정 충돌은 saveAndFlush에서 감지한 낙관적 락 예외로 변환합니다.
 */
@Service
@RequiredArgsConstructor
public class TravelScheduleService {

    private final UserRepository userRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final Clock clock;

    @Transactional
    public TravelSchedule create(Long userId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate);
        validateStartDateNotInPast(startDate);
        validateNoOverlappingSchedule(userId, startDate, endDate, null);
        TravelSchedule schedule = TravelSchedule.create(findUser(userId), startDate, endDate);
        return travelScheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public List<TravelSchedule> getSchedules(Long userId) {
        findUser(userId);
        return travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(userId);
    }

    /**
     * 본인 소유 일정의 기간을 변경하되 날짜 누락·역전·과거 시작과 다른 유효 일정과의 중복은 거절합니다.
     * 수정 불가 상태와 flush 시 낙관적 잠금 충돌을 각각 일정 오류로 변환하고 저장한 일정을 반환합니다.
     */
    @Transactional
    public TravelSchedule update(Long userId, Long scheduleId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate);
        validateStartDateNotInPast(startDate);
        TravelSchedule schedule = findSchedule(userId, scheduleId);
        validateNoOverlappingSchedule(userId, startDate, endDate, scheduleId);
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

    private void validateStartDateNotInPast(LocalDate startDate) {
        if (startDate.isBefore(today())) {
            throw new UsersException(UsersErrorCode.TRAVEL_SCHEDULE_START_DATE_IN_PAST);
        }
    }

    /**
     * 현재 트랜잭션에서 취소되지 않은 일정과의 날짜 중복을 조회합니다.
     * 이 사전 조회 자체가 서로 다른 요청의 동시 일정 생성을 직렬화하는 잠금은 아닙니다.
     */
    private void validateNoOverlappingSchedule(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            Long excludedScheduleId
    ) {
        boolean overlapping = travelScheduleRepository.existsOverlappingSchedule(
                userId,
                TravelScheduleState.SCHEDULED,
                startDate,
                endDate,
                excludedScheduleId
        );
        if (overlapping) {
            throw new UsersException(UsersErrorCode.TRAVEL_SCHEDULE_PERIOD_OVERLAP);
        }
    }
}
