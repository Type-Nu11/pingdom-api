package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenResponse;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.application.service.AuthService;
import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long EMAIL_VERIFICATION_EXPIRATION_MINUTES = 10L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OutboxEventPublisher outboxEventPublisher;
    private final UserWithdrawalDataService userWithdrawalDataService;

    @Override
    @Transactional
    // 이메일 포함 회원가입 저장 기능
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new AuthException(AuthErrorCode.DUPLICATE_USERNAME);
        }
        if (StringUtils.hasText(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new AuthException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .birthYear(request.birthYear())
                .profileImageUrl(request.profileImageUrl())
                .language(request.language())
                .country(request.country())
                .build();

        if (StringUtils.hasText(request.email())) {
            // 이메일 인증 코드 발급 처리
            issueEmailVerification(user);
        }

        User savedUser = userRepository.save(user);

        storeEmailVerificationOutboxEvent(savedUser);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getBirthYear(),
                savedUser.getProfileImageUrl(),
                savedUser.getLanguage(),
                savedUser.getCountry()
        );
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = authenticateUser(request);
        return issueLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse adminLogin(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!user.isAdmin()) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (user.isBanned()) {
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

    @Override
    @Transactional
    public void resendVerificationEmail(EmailResendRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isBanned()) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (user.isEmailVerified()) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        issueEmailVerification(user);
        storeEmailVerificationOutboxEvent(user);
    }

    @Override
    @Transactional
    // 이메일 기준 사용자 인증 처리 메서드
    public void verifyEmail(EmailVerifyRequest request) {
        User user = userRepository.findByEmailAndEmailVerificationCode(request.email(), request.code())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE));

        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (user.isEmailVerificationExpired(LocalDateTime.now())) {
            throw new AuthException(AuthErrorCode.EXPIRED_EMAIL_VERIFICATION_CODE);
        }

        // 이메일 인증 상태 반영 호출
        user.verifyEmail();
    }

    @Override
    @Transactional
    // Refresh Token 기준 토큰 재발급 메서드
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        Long userId = extractValidRefreshTokenUserId(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (!user.matchesRefreshToken(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 재발급용 Access Token, Refresh Token 생성 호출
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 새 Refresh Token 회전 반영 호출
        user.issueRefreshToken(refreshToken);

        return new RefreshTokenResponse(accessToken, refreshToken);
    }

    @Override
    @Transactional
    // Refresh Token 무효화 기반 로그아웃 메서드
    public void logout(RefreshTokenRequest request) {
        Long userId = extractValidRefreshTokenUserId(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            return;
        }

        if (!user.matchesRefreshToken(request.refreshToken())) {
            return;
        }

        // 현재 활성 Refresh Token 제거로 재발급 경로 차단
        user.clearRefreshToken();
    }

    // 6자리 이메일 인증 코드 생성 메서드
    private String generateVerificationCode() {
        return "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private void issueEmailVerification(User user) {
        user.issueEmailVerification(
                generateVerificationCode(),
                LocalDateTime.now().plusMinutes(EMAIL_VERIFICATION_EXPIRATION_MINUTES)
        );
    }

    private void storeEmailVerificationOutboxEvent(User user) {
        if (StringUtils.hasText(user.getEmail()) && StringUtils.hasText(user.getEmailVerificationCode())) {
            outboxEventPublisher.publish(
                    "EMAIL_VERIFICATION:%s:%s:%s".formatted(
                            user.getId(),
                            user.getEmailVerificationCode(),
                            user.getEmailVerificationExpiresAt()
                    ),
                    OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                    new EmailVerificationOutboxPayload(user.getEmail(), user.getEmailVerificationCode()),
                    "USER",
                    String.valueOf(user.getId())
            );
        }
    }

    @Override
    @Transactional
    // 회원탈퇴 익명화 및 보존 상태 전환 메서드
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            return;
        }

        user.withdraw(
                anonymizedUsername(user.getId()),
                anonymizedEmail(user.getId()),
                passwordEncoder.encode(UUID.randomUUID().toString()),
                LocalDateTime.now()
        );
        userWithdrawalDataService.cleanupUserOwnedData(user.getId());
    }

    private Long extractValidRefreshTokenUserId(String refreshToken) {
        JwtTokenProvider.RefreshTokenParseResult parsed = jwtTokenProvider.parseRefreshToken(refreshToken);
        if (parsed.status() != JwtTokenProvider.TokenStatus.VALID || parsed.userId() == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return parsed.userId();
    }

    private User authenticateUser(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

        if (user.isBanned()) {
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

    private LoginResponse issueLoginResponse(User user) {
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        // 로그인 성공 시 JWT 발급 호출
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 현재 활성 Refresh Token 저장 호출
        user.issueRefreshToken(refreshToken);

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBirthYear(),
                user.getProfileImageUrl(),
                user.getLanguage(),
                user.getCountry(),
                "로그인에 성공했습니다.",
                accessToken,
                refreshToken
        );
    }

    private String anonymizedUsername(Long userId) {
        return "withdrawn_user_" + userId;
    }

    private String anonymizedEmail(Long userId) {
        return "withdrawn_user_%d@withdrawn.local".formatted(userId);
    }
}
