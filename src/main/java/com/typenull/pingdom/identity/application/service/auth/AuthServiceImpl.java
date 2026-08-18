package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.application.service.withdrawal.UserWithdrawalDataService;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;
import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;
import com.typenull.pingdom.notification.outbox.PasswordResetOutboxPayload;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long EMAIL_VERIFICATION_EXPIRATION_MINUTES = 10L;
    private static final long PASSWORD_RESET_EXPIRATION_MINUTES = 30L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final UserWithdrawalDataService userWithdrawalDataService;
    private final UserAccessStatusService userAccessStatusService;
    private final Clock clock;
    private final AuthMetrics authMetrics;

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
                savedUser.getCountry(),
                savedUser.getRole()
        );
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = authenticateUser(request);
        return issueLoginResponse(user);
    }

    @Override
    @Transactional
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

    @Override
    @Transactional
    public void resendVerificationEmail(EmailResendRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isCurrentlyBanned(now())) {
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

        if (user.isEmailVerificationExpired(now())) {
            throw new AuthException(AuthErrorCode.EXPIRED_EMAIL_VERIFICATION_CODE);
        }

        // 이메일 인증 상태 반영 호출
        user.verifyEmail();
    }

    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(user -> !user.isWithdrawn())
                .filter(user -> !user.isCurrentlyBanned(now()))
                .ifPresent(this::issuePasswordReset);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        request.validatePassword();

        LocalDateTime now = now();
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHashForUpdate(passwordResetTokenHash(request.token()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        if (resetToken.isUsed()) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        if (resetToken.isExpired(now)) {
            throw new AuthException(AuthErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);
        }

        User user = userRepository.findByIdForUpdate(resetToken.getUser().getId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.email().trim())) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }
        if (user.isCurrentlyBanned(now)) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.clearRefreshToken();
        resetToken.markUsed(now);
        passwordResetTokenRepository.markActiveTokensUsed(user.getId(), now);
    }

    @Override
    @Transactional
    // Refresh Token 기준 토큰 재발급 메서드
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

    @Override
    @Transactional
    // Refresh Token 무효화 기반 로그아웃 메서드
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

    // 6자리 이메일 인증 코드 생성 메서드
    private String generateVerificationCode() {
        return "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private void issueEmailVerification(User user) {
        user.issueEmailVerification(
                generateVerificationCode(),
                now().plusMinutes(EMAIL_VERIFICATION_EXPIRATION_MINUTES)
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
                    new EmailVerificationOutboxPayload(user.getId(), user.getEmail(), user.getEmailVerificationCode()),
                    "USER",
                    String.valueOf(user.getId())
            );
        }
    }

    private void issuePasswordReset(User user) {
        LocalDateTime issuedAt = now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(PASSWORD_RESET_EXPIRATION_MINUTES);
        String resetToken = generatePasswordResetToken();
        String tokenHash = passwordResetTokenHash(resetToken);

        passwordResetTokenRepository.markActiveTokensUsed(user.getId(), issuedAt);
        passwordResetTokenRepository.save(PasswordResetToken.create(user, tokenHash, expiresAt, issuedAt));
        storePasswordResetOutboxEvent(user, resetToken, tokenHash, expiresAt);
    }

    private String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String passwordResetTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.", exception);
        }
    }

    private void storePasswordResetOutboxEvent(
            User user,
            String resetToken,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        outboxEventPublisher.publish(
                "PASSWORD_RESET:%s:%s:%s".formatted(user.getId(), tokenHash, expiresAt),
                OutboxEventType.PASSWORD_RESET_REQUESTED,
                new PasswordResetOutboxPayload(user.getId(), user.getEmail(), resetToken, expiresAt),
                "USER",
                String.valueOf(user.getId())
        );
    }

    @Override
    @Transactional
    // 회원탈퇴 익명화 및 보존 상태 전환 메서드
    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            return;
        }

        eventPublisher.publishEvent(PrivacyProcessingEvent.userAction(
                user.getId(),
                PrivacyProcessingAction.WITHDRAWAL_REQUESTED,
                "회원 탈퇴 요청"
        ));
        user.withdraw(
                anonymizedUsername(user.getId()),
                anonymizedEmail(user.getId()),
                "WITHDRAWN_" + UUID.randomUUID(),
                now()
        );
        eventPublisher.publishEvent(PrivacyProcessingEvent.userAction(
                user.getId(),
                PrivacyProcessingAction.ANONYMIZED,
                "회원 탈퇴에 따른 개인정보 익명화"
        ));
        userAccessStatusService.evict(user.getId());
        userWithdrawalDataService.cleanupUserOwnedData(user.getId());
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

    private String anonymizedUsername(Long userId) {
        return "withdrawn_user_" + userId;
    }

    private String anonymizedEmail(Long userId) {
        return "withdrawn_user_%d@withdrawn.local".formatted(userId);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
