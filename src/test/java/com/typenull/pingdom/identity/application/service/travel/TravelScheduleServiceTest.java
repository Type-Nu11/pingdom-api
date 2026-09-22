package com.typenull.pingdom.identity.application.service.travel;


import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class TravelScheduleServiceTest {

    private static final long USER_ID = 1L;
    private static final long SCHEDULE_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TravelScheduleRepository travelScheduleRepository;

    @Mock
    private Clock clock;

    /**
     * 일정 취소 저장의 낙관적 잠금 실패를 TRAVEL_SCHEDULE_CONCURRENT_MODIFICATION으로 변환하는지 검증.
     */
    @Test
    void mapsConcurrentScheduleModification() {
        TravelScheduleService service = new TravelScheduleService(userRepository, travelScheduleRepository, clock);
        TravelSchedule schedule = TravelSchedule.create(
                User.builder().id(USER_ID).build(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3)
        );
        when(travelScheduleRepository.findByIdAndUser_Id(SCHEDULE_ID, USER_ID)).thenReturn(Optional.of(schedule));
        doThrow(new ObjectOptimisticLockingFailureException(TravelSchedule.class, SCHEDULE_ID))
                .when(travelScheduleRepository).saveAndFlush(schedule);

        assertThatThrownBy(() -> service.cancel(USER_ID, SCHEDULE_ID))
                .isInstanceOfSatisfying(UsersException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(UsersErrorCode.TRAVEL_SCHEDULE_CONCURRENT_MODIFICATION)
                );
    }

    /**
     * 오늘 이전 시작일의 생성 요청은 TRAVEL_SCHEDULE_START_DATE_IN_PAST이며 기간 중복 조회에도 도달하지 않는지 검증.
     */
    @Test
    void rejectsScheduleStartingBeforeToday() {
        TravelScheduleService service = serviceAt(LocalDate.of(2026, 8, 10));

        assertThatThrownBy(() -> service.create(
                USER_ID,
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 12)
        )).isInstanceOfSatisfying(UsersException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                        .isEqualTo(UsersErrorCode.TRAVEL_SCHEDULE_START_DATE_IN_PAST)
        );

        verify(travelScheduleRepository, never()).existsOverlappingSchedule(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    /**
     * 기존 SCHEDULED 일정과 생성 기간이 겹치면 TRAVEL_SCHEDULE_PERIOD_OVERLAP으로 거절되는지 검증.
     */
    @Test
    void rejectsOverlappingScheduleCreation() {
        TravelScheduleService service = serviceAt(LocalDate.of(2026, 8, 10));
        when(travelScheduleRepository.existsOverlappingSchedule(
                USER_ID,
                TravelScheduleState.SCHEDULED,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13),
                null
        )).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                USER_ID,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13)
        )).isInstanceOfSatisfying(UsersException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                        .isEqualTo(UsersErrorCode.TRAVEL_SCHEDULE_PERIOD_OVERLAP)
        );
    }

    /**
     * 수정 대상 ID를 제외한 다른 일정과 새 기간이 겹치면 TRAVEL_SCHEDULE_PERIOD_OVERLAP인지 검증.
     */
    @Test
    void rejectsOverlappingScheduleUpdate() {
        TravelScheduleService service = serviceAt(LocalDate.of(2026, 8, 10));
        TravelSchedule schedule = TravelSchedule.create(
                User.builder().id(USER_ID).build(),
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 12)
        );
        when(travelScheduleRepository.findByIdAndUser_Id(SCHEDULE_ID, USER_ID))
                .thenReturn(Optional.of(schedule));
        when(travelScheduleRepository.existsOverlappingSchedule(
                USER_ID,
                TravelScheduleState.SCHEDULED,
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 15),
                SCHEDULE_ID
        )).thenReturn(true);

        assertThatThrownBy(() -> service.update(
                USER_ID,
                SCHEDULE_ID,
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 15)
        )).isInstanceOfSatisfying(UsersException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                        .isEqualTo(UsersErrorCode.TRAVEL_SCHEDULE_PERIOD_OVERLAP)
        );
    }

    /**
     * 지정 날짜 UTC 자정을 현재로 사용하는 서비스를 만들어 과거 날짜와 중복 기간의 입력을 고정.
     */
    private TravelScheduleService serviceAt(LocalDate today) {
        return new TravelScheduleService(
                userRepository,
                travelScheduleRepository,
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }
}
