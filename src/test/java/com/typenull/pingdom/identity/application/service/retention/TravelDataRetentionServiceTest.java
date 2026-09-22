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

    /**
     * 보존 기간 7일·정리 배치 100건과 고정 Clock을 사용하는 여행 데이터 정리 서비스를 구성한다.
     */
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

    /**
     * 만료 의도 2건과 7일 전 탈퇴자의 의도 1건·일정 3건을 합계 6건으로 보고하는지 검증한다.
     * 탈퇴 기준 시각·첫 100건 조회와 대상 사용자별 삭제 전달도 확인한다.
     */
    @Test
    void purgesExpiredAndWithdrawnTravelData() {
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
