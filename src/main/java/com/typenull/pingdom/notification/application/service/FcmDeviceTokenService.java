package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.exception.NotificationsErrorCode;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.infrastructure.persistence.FcmDeviceTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 탈퇴하지 않은 사용자의 FCM 토큰 등록·삭제를 관리.
 * 이미 존재하는 토큰은 현재 사용자로 소유자를 바꾸고 등록 시각을 갱신하며, 동일 토큰 동시 신규 등록의 유일 제약 오류는 재시도 대상에서 제외.
 */
@Service
@RequiredArgsConstructor
public class FcmDeviceTokenService {

    private final UserRepository userRepository;
    private final FcmDeviceTokenRepository fcmDeviceTokenRepository;
    private final Clock clock;

    /**
     * 존재하며 탈퇴하지 않은 사용자의 토큰을 공백 제거 후 등록. 빈 토큰은 거부.
     * 같은 토큰이 있으면 소유자와 갱신 시각을 현재 요청으로 바꾸며, 공급자 토큰 유효성은 실제 전송 시 확인.
     */
    @Transactional
    public void registerToken(Long userId, String token) {
        ensureActiveUser(userId);
        String normalizedToken = normalizeToken(token);
        LocalDateTime now = LocalDateTime.now(clock);

        fcmDeviceTokenRepository.findByToken(normalizedToken)
                .ifPresentOrElse(
                        existingToken -> existingToken.refresh(userId, now),
                        () -> fcmDeviceTokenRepository.save(FcmDeviceToken.create(userId, normalizedToken, now))
                );
    }

    @Transactional
    public void deleteToken(Long userId, String token) {
        ensureActiveUser(userId);
        fcmDeviceTokenRepository.deleteByUserIdAndToken(userId, normalizeToken(token));
    }

    @Transactional(readOnly = true)
    public List<FcmDeviceToken> findTokens(Long userId) {
        return fcmDeviceTokenRepository.findAllByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public void deleteInvalidToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        fcmDeviceTokenRepository.deleteByToken(token.trim());
    }

    private void ensureActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new NotificationsException(NotificationsErrorCode.INVALID_FCM_TOKEN);
        }
        return token.trim();
    }
}
