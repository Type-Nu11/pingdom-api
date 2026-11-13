package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AuthService facade의 쓰기 트랜잭션에 참여해 로그인·세션 회전을 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthSessionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final Clock clock;
    private final AuthMetrics authMetrics;

    public LoginResult login(LoginRequest request) {
        User user = authenticateUser(request);
        return issueLoginResponse(user);
    }

    public LoginResult adminLogin(LoginRequest request) {
        User user = userRepository.findByUsernameForUpdate(request.username())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!user.isAdmin()) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (user.isCurrentlyBanned(now())) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return issueLoginResponse(user);
    }

    public TokenRefreshResult refreshToken(String refreshToken) {
        try {
            Long userId = extractValidRefreshTokenUserId(refreshToken);
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

            if (user.isWithdrawn()) {
                throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
            }

            if (user.isCurrentlyBanned(now())) {
                throw new AuthException(AuthErrorCode.USER_BANNED);
            }

            // 회원 행 잠금 안에서 현재 토큰을 대조하므로 회전이 완료된 뒤 이전 토큰을 재사용하면 거부됨.
            if (!user.matchesRefreshToken(refreshToken)) {
                throw new AuthException(AuthErrorCode.INVALID_TOKEN);
            }

            // 재발급용 Access Token, Refresh Token 생성 호출
            String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
            String rotatedRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            // 새 Refresh Token 회전 반영 호출
            user.issueRefreshToken(rotatedRefreshToken);
            authMetrics.recordRefreshTokenSuccess();

            return new TokenRefreshResult(accessToken, rotatedRefreshToken);
        } catch (RuntimeException exception) {
            authMetrics.recordRefreshTokenFailure(refreshTokenFailureReason(exception));
            throw exception;
        }
    }

    public void logout(String refreshToken) {
        Long userId = extractValidRefreshTokenUserId(refreshToken);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            return;
        }

        if (!user.matchesRefreshToken(refreshToken)) {
            return;
        }

        // 현재 활성 Refresh Token 제거로 재발급 경로 차단
        user.clearRefreshToken();
    }

    private Long extractValidRefreshTokenUserId(String refreshToken) {
        JwtTokenProvider.RefreshTokenParseResult parsed = jwtTokenProvider.parseRefreshToken(refreshToken);
        if (parsed.status() != JwtTokenProvider.TokenStatus.VALID || parsed.userId() == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return parsed.userId();
    }

    private String refreshTokenFailureReason(RuntimeException exception) {
        if (exception instanceof AuthException authException) {
            return authException.getErrorCode().name();
        }
        return exception.getClass().getSimpleName();
    }

    private User authenticateUser(LoginRequest request) {
        User user = userRepository.findByUsernameForUpdate(request.username())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

        if (user.isCurrentlyBanned(now())) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return user;
    }

    private LoginResult issueLoginResponse(User user) {
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        // 로그인 성공 시 JWT 발급 호출
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 현재 활성 Refresh Token 저장 호출
        user.issueRefreshToken(refreshToken);

        return new LoginResult(
                new LoginResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getBirthYear(),
                        user.getProfileImageUrl(),
                        user.getLanguage(),
                        user.getCountry(),
                        "로그인에 성공했습니다.",
                        accessToken,
                        user.getRole()
                ),
                refreshToken
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
