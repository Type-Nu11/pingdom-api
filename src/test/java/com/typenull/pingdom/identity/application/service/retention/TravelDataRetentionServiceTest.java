package com.typenull.pingdom.identity.application.service.retention;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TravelDataRetentionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TravelScheduleRepository travelScheduleRepository;

    @Mock
    private UserCurrentActivityIntentRepository currentActivityIntentRepository;

    private TravelDataRetentionService travelDataRetentionService;

    @BeforeEach
    void setUp() {
        travelDataRetentionService = new TravelDataRetentionService(
                userRepository,
                travelScheduleRepository,
                currentActivityIntentRepository,
                new TravelDataRetentionProperties(Duration.ofDays(7), 100),
                Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void removesExpiredIntentAndTravelDataOfUsersWithdrawnForSevenDays() {
        when(currentActivityIntentRepository.deleteExpiredAtOrBefore(any(LocalDateTime.class))).thenReturn(2);
        when(userRepository.findExpiredWithdrawnUserIdsWithTravelData(eq(UserStatus.WITHDRAWN), any(LocalDateTime.class), any()))
                .thenReturn(List.of(10L, 11L));
        when(currentActivityIntentRepository.deleteAllByUserIds(List.of(10L, 11L))).thenReturn(1);
        when(travelScheduleRepository.deleteAllByUserIds(List.of(10L, 11L))).thenReturn(3);

        TravelDataRetentionService.TravelDataRetentionResult result = travelDataRetentionService.purgeExpiredData();

        assertThat(result.totalDeletedCount()).isEqualTo(6);
        verify(userRepository).findExpiredWithdrawnUserIdsWithTravelData(
                UserStatus.WITHDRAWN,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                org.springframework.data.domain.PageRequest.of(0, 100)
        );
        verify(currentActivityIntentRepository).deleteAllByUserIds(List.of(10L, 11L));
        verify(travelScheduleRepository).deleteAllByUserIds(List.of(10L, 11L));
    }
}
