package com.typenull.pingdom.shared.security.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAccessStatusServiceTest {

    private static final Long USER_ID = 1L;
    private static final Instant BASE_TIME = Instant.parse("2026-06-23T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    private MutableClock clock;
    private UserAccessStatusService userAccessStatusService;

    /**
     * 변경 가능한 UTC Clock을 접근 상태 서비스에 주입해 캐시 만료를 실제 대기 없이 검증.
     */
    @BeforeEach
    void setUp() {
        clock = new MutableClock(BASE_TIME, ZoneOffset.UTC);
        userAccessStatusService = new UserAccessStatusService(userRepository, clock);
    }

    /**
     * 5초 뒤 끝나는 임시 밴의 접근 거절은 4초까지 캐시되고 6초 시점에는 DB 재조회 후 허용되는지 검증.
     * 기본 TTL이 밴 해제 이후까지 거절을 유지하는 회귀를 방지.
     */
    @Test
    void expiresCacheAtTemporaryBanEnd() {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = activeUser();
        user.ban("임시 밴 캐시 경계", now.minusSeconds(1), now.plusSeconds(5));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertFalse(userAccessStatusService.canAuthenticate(USER_ID));

        clock.advance(Duration.ofSeconds(4));
        assertFalse(userAccessStatusService.canAuthenticate(USER_ID));
        verify(userRepository, times(1)).findById(USER_ID);

        clock.advance(Duration.ofSeconds(2));
        assertTrue(userAccessStatusService.canAuthenticate(USER_ID));
        verify(userRepository, times(2)).findById(USER_ID);
    }

    /**
     * 허용 결과를 캐시한 뒤 evict하면 사용자 없음 상태를 다시 조회해 거절로 반영하고 총 DB 조회가 두 번인지 검증.
     */
    @Test
    void evictRemovesCachedAccessDecision() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(activeUser()))
                .thenReturn(Optional.empty());

        assertTrue(userAccessStatusService.canAuthenticate(USER_ID));

        userAccessStatusService.evict(USER_ID);

        assertFalse(userAccessStatusService.canAuthenticate(USER_ID));
        verify(userRepository, times(2)).findById(USER_ID);
    }

    /**
     * 정해진 ID와 필수 프로필을 가진 미차단 사용자를 만들어 접근 판단의 정상 기준을 제공.
     */
    private User activeUser() {
        return User.builder()
                .id(USER_ID)
                .username("accessStatusUser")
                .email("access-status@example.com")
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build();
    }

    private static class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        /**
         * 초기 Instant와 시간대를 보관해 접근 캐시의 시간 입력을 제어.
         */
        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        /**
         * 생성 시 지정한 시간대를 반환해 사용자 밴의 LocalDateTime 판정 기준을 유지.
         */
        @Override
        public ZoneId getZone() {
            return zone;
        }

        /**
         * 같은 현재 Instant와 새 시간대를 가진 독립 테스트 Clock을 생성.
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        /**
         * 테스트가 제어하는 현재 Instant를 반환.
         */
        @Override
        public Instant instant() {
            return instant;
        }

        /**
         * 현재 시각을 지정 기간만큼 전진시켜 밴 만료와 캐시 재조회 경계를 재현.
         */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
