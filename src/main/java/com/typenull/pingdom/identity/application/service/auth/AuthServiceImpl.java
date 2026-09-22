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
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
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

/** 회원가입, 로그인, 이메일·비밀번호 인증, 토큰 갱신과 탈퇴 흐름을 조정. */
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
    private final PrivacyProcessingOutboxPublisher privacyProcessingOutboxPublisher;
    private final UserWithdrawalDataService userWithdrawalDataService;
    private final UserAccessStatusService userAccessStatusService;
    private final Clock clock;
    private final AuthMetrics authMetrics;

    /**
     * 중복 사용자명·이메일을 거절하고 암호화한 비밀번호로 회원을 저장해 가입 응답을 반환.
     * 이메일이 있으면 10분 유효 인증 코드를 발급하고 이메일 발송 요청을 Outbox에 기록.
     */
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

    /** 자격 증명과 계정 상태를 확인한 뒤 access·refresh token을 발급. */
    @Override
    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = authenticateUser(request);
        return issueLoginResponse(user);
    }

    /**
     * 사용자명으로 회원 행을 잠근 뒤 ADMIN 여부·정지·탈퇴 상태와 비밀번호를 확인.
     * 검증 실패는 인증 오류로 거절하고 성공하면 access token과 새 refresh token을 발급해 후자를 회원에 저장.
     */
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

    /**
     * 이메일로 회원을 찾아 정지·탈퇴·인증 완료 상태를 거절한 뒤 10분 유효 인증 코드를 새로 발급.
     * 새 코드 발송을 Outbox에 요청하며 이메일이 없는 회원 조회는 USER_NOT_FOUND로 실패.
     */
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

    /**
     * 이메일과 인증 코드가 일치하는 회원의 탈퇴 여부와 코드 만료를 확인한 뒤 이메일을 인증 완료로 변경.
     * 일치하는 코드가 없거나 만료됐으면 각각 인증 코드 오류로 거절.
     */
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

    /**
     * 존재하며 탈퇴·정지되지 않은 이메일에만 재설정 토큰을 발급.
     * 해당하지 않는 이메일도 동일한 정상 반환 경로를 사용하여 이 메서드의 응답에서는 계정 존재 여부를 구분 불가.
     */
    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(user -> !user.isWithdrawn())
                .filter(user -> !user.isCurrentlyBanned(now()))
                .ifPresent(this::issuePasswordReset);
    }

    /**
     * 토큰 행과 사용자 행을 잠그고 만료·사용 여부·이메일·계정 상태를 확인한 후 비밀번호를 교체.
     * 저장된 refresh token과 남아 있는 재설정 토큰도 무효화하며 이미 발급된 access token은 회수 대상에서 제외.
     */
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

    /** 쿠키의 refresh token과 저장된 세션을 대조해 access token을 재발급. */
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

    /**
     * 유효한 refresh token의 회원 행을 잠그고 현재 저장 토큰과 일치할 때만 세션 토큰을 제거.
     * 탈퇴 회원이나 이미 교체된 토큰은 변경 없이 종료하고, 잘못된 토큰·없는 회원은 인증 오류로 거절.
     */
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

    /**
     * 기존 미사용 토큰을 사용 처리한 뒤 새 토큰의 SHA-256 해시를 저장.
     * 전달용 원문 토큰은 이메일 발송을 위한 outbox payload에 포함됨.
     */
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

    /**
     * 회원 행을 잠그고 아직 탈퇴하지 않은 회원의 식별 정보를 익명화한 뒤 보존 대상 탈퇴 상태로 전환.
     * 탈퇴 요청·익명화 Outbox 기록, 접근 상태 캐시 제거와 사용자 소유 데이터 정리를 수행하며 반복 탈퇴는 그대로 종료.
     */
    @Override
    @Transactional
    // 회원탈퇴 익명화 및 보존 상태 전환 메서드
    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            return;
        }

        privacyProcessingOutboxPublisher.publish(PrivacyProcessingEvent.userAction(
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
        privacyProcessingOutboxPublisher.publish(PrivacyProcessingEvent.userAction(
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
