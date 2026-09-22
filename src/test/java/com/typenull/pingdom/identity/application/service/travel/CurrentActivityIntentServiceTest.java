package com.typenull.pingdom.identity.application.service.travel;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentActivityIntentServiceTest {

    private static final long USER_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCurrentActivityIntentRepository currentActivityIntentRepository;

    private CurrentActivityIntentService currentActivityIntentService;

    /**
     * 사용자·의도 저장소 대역과 UTC 고정 Clock을 연결해 서버 관리 만료를 검증한다.
     */
    @BeforeEach
    void setUp() {
        currentActivityIntentService = new CurrentActivityIntentService(
                userRepository,
                currentActivityIntentRepository,
                CLOCK
        );
    }

    /**
     * 사용자 잠금 조회 후 새 CAFE 의도를 저장하면 요청값과 서버 현재 시각 2시간 뒤 만료가 반영되는지 검증한다.
     */
    @Test
    void setsTwoHourIntentExpiry() {
        User user = User.builder().id(USER_ID).build();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(currentActivityIntentRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(currentActivityIntentRepository.save(any(UserCurrentActivityIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCurrentActivityIntent result = currentActivityIntentService.replace(USER_ID, CurrentActivityIntent.CAFE);

        assertThat(result.getIntent()).isEqualTo(CurrentActivityIntent.CAFE);
        assertThat(result.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 12, 0));
    }

    /**
     * 만료 시각에 도달한 의도 조회는 null을 반환하고 읽기 중 저장소 삭제를 실행하지 않는지 검증한다.
     */
    @Test
    void hidesExpiredIntentWithoutDeletion() {
        User user = User.builder().id(USER_ID).build();
        UserCurrentActivityIntent expiredIntent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.EAT,
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(currentActivityIntentRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(expiredIntent));

        UserCurrentActivityIntent result = currentActivityIntentService.getCurrentIntent(USER_ID);

        assertThat(result).isNull();
        verify(currentActivityIntentRepository, never()).delete(expiredIntent);
    }
}
