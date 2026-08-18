package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.infrastructure.persistence.FcmDeviceTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmDeviceTokenServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FcmDeviceTokenRepository fcmDeviceTokenRepository;

    private FcmDeviceTokenService fcmDeviceTokenService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);
        fcmDeviceTokenService = new FcmDeviceTokenService(userRepository, fcmDeviceTokenRepository, clock);
    }

    @Test
    void registerTokenStoresTrimmedToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser(USER_ID)));
        when(fcmDeviceTokenRepository.findByToken("device-token")).thenReturn(Optional.empty());

        fcmDeviceTokenService.registerToken(USER_ID, " device-token ");

        ArgumentCaptor<FcmDeviceToken> captor = ArgumentCaptor.forClass(FcmDeviceToken.class);
        verify(fcmDeviceTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getToken()).isEqualTo("device-token");
    }

    @Test
    void registerTokenReassignsExistingTokenToCurrentUser() {
        long newUserId = 2L;
        FcmDeviceToken existingToken = FcmDeviceToken.create(USER_ID, "device-token", java.time.LocalDateTime.now());
        when(userRepository.findById(newUserId)).thenReturn(Optional.of(activeUser(newUserId)));
        when(fcmDeviceTokenRepository.findByToken("device-token")).thenReturn(Optional.of(existingToken));

        fcmDeviceTokenService.registerToken(newUserId, "device-token");

        assertThat(existingToken.getUserId()).isEqualTo(newUserId);
    }

    @Test
    void blankTokenIsRejected() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser(USER_ID)));

        assertThatThrownBy(() -> fcmDeviceTokenService.registerToken(USER_ID, " "))
                .isInstanceOf(NotificationsException.class);
    }

    @Test
    void withdrawnUserCannotRegisterToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(withdrawnUser(USER_ID)));

        assertThatThrownBy(() -> fcmDeviceTokenService.registerToken(USER_ID, "device-token"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void deleteTokenDeletesOnlyCurrentUsersToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser(USER_ID)));

        fcmDeviceTokenService.deleteToken(USER_ID, " device-token ");

        verify(fcmDeviceTokenRepository).deleteByUserIdAndToken(USER_ID, "device-token");
    }

    private User activeUser(Long userId) {
        return User.builder()
                .id(userId)
                .username("user" + userId)
                .email("user" + userId + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build();
    }

    private User withdrawnUser(Long userId) {
        return User.builder()
                .id(userId)
                .username("withdrawn" + userId)
                .email("withdrawn" + userId + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .status(UserStatus.WITHDRAWN)
                .build();
    }
}
