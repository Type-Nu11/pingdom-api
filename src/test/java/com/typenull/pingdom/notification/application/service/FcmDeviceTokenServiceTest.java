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

    /**
     * 토큰 등록 시각이 테스트마다 달라지지 않도록 고정 Clock으로 서비스를 구성.
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);
        fcmDeviceTokenService = new FcmDeviceTokenService(userRepository, fcmDeviceTokenRepository, clock);
    }

    /**
     * 활성 사용자가 공백을 포함한 토큰을 등록하면 양끝 공백을 제거하고 해당 사용자 ID와 함께 저장하는지 검증.
     */
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

    /**
     * 다른 사용자 소유의 기존 토큰을 현재 활성 사용자가 등록하면 소유자 ID를 변경하는지 검증.
     */
    @Test
    void reassignsExistingDeviceToken() {
        long newUserId = 2L;
        FcmDeviceToken existingToken = FcmDeviceToken.create(USER_ID, "device-token", java.time.LocalDateTime.now());
        when(userRepository.findById(newUserId)).thenReturn(Optional.of(activeUser(newUserId)));
        when(fcmDeviceTokenRepository.findByToken("device-token")).thenReturn(Optional.of(existingToken));

        fcmDeviceTokenService.registerToken(newUserId, "device-token");

        assertThat(existingToken.getUserId()).isEqualTo(newUserId);
    }

    /**
     * 활성 사용자의 공백뿐인 토큰 등록 요청을 알림 도메인 예외로 거절하는지 검증.
     */
    @Test
    void blankTokenIsRejected() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser(USER_ID)));

        assertThatThrownBy(() -> fcmDeviceTokenService.registerToken(USER_ID, " "))
                .isInstanceOf(NotificationsException.class);
    }

    /**
     * 탈퇴 사용자의 기기 토큰 등록을 인증 예외로 거절하는지 검증.
     */
    @Test
    void withdrawnUserCannotRegisterToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(withdrawnUser(USER_ID)));

        assertThatThrownBy(() -> fcmDeviceTokenService.registerToken(USER_ID, "device-token"))
                .isInstanceOf(AuthException.class);
    }

    /**
     * 토큰 삭제 시 공백을 제거한 토큰과 현재 사용자 ID를 함께 조건으로 전달하는지 검증.
     */
    @Test
    void deletesOnlyOwnedDeviceToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser(USER_ID)));

        fcmDeviceTokenService.deleteToken(USER_ID, " device-token ");

        verify(fcmDeviceTokenRepository).deleteByUserIdAndToken(USER_ID, "device-token");
    }

    /**
     * 기기 토큰을 등록·삭제할 수 있는 활성 사용자를 지정 ID로 생성.
     */
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

    /**
     * 탈퇴 상태의 인증 차단을 확인할 사용자를 지정 ID로 생성.
     */
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
