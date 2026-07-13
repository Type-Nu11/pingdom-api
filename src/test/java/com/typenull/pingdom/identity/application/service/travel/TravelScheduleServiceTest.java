package com.typenull.pingdom.identity.application.service.travel;


import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import java.time.Clock;
import java.time.LocalDate;
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

    @Test
    void returnsConflictWhenConcurrentScheduleChangeIsDetected() {
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
}
