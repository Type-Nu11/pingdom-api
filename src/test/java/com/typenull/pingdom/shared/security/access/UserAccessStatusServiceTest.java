package com.typenull.pingdom.shared.security.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.UserAccessStatusService;
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

    @BeforeEach
    void setUp() {
        clock = new MutableClock(BASE_TIME, ZoneOffset.UTC);
        userAccessStatusService = new UserAccessStatusService(userRepository, clock);
    }

    @Test
    void temporaryBanCacheExpiresAtBanExpirationBeforeDefaultTtl() {
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

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
