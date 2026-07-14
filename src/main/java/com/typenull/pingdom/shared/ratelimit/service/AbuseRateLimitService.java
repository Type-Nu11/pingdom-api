package com.typenull.pingdom.shared.ratelimit.service;

import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.WindowPolicy;

import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitCooldownRule;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitWindowRule;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class AbuseRateLimitService {

    private static final String DEFAULT_MESSAGE = "요청 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String EMAIL_MESSAGE = "인증 메일 재발송 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String SIGNUP_MESSAGE = "회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String EMAIL_VERIFY_MESSAGE = "이메일 인증 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String PASSWORD_RESET_MESSAGE = "비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";

    private final AbuseRateLimitProperties properties;
    private final RateLimitStore store;

    public AbuseRateLimitService(AbuseRateLimitProperties properties, RateLimitStore store) {
        this.properties = properties;
        this.store = store;
    }

    public void checkSignup(String email, String clientIp) {
        String emailFingerprint = fingerprint(normalize(email));
        acquireWithLogging(
                "signup",
                "emailFingerprint=" + emailFingerprint + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        SIGNUP_MESSAGE,
                        List.of(
                                windowRule("signup:email:" + emailFingerprint, properties.signupEmail()),
                                windowRule("signup:ip:" + normalizeIp(clientIp), properties.signupIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkLogin(String username, String clientIp) {
        acquireWithLogging(
                "login",
                "username=" + normalize(username) + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        DEFAULT_MESSAGE,
                        List.of(
                                windowRule("login:username:" + fingerprint(normalize(username)), properties.loginUsername()),
                                windowRule("login:ip:" + normalizeIp(clientIp), properties.loginIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkTokenRefresh(String refreshToken, String clientIp) {
        acquireWithLogging(
                "token-refresh",
                "tokenFingerprint=" + fingerprint(refreshToken) + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        DEFAULT_MESSAGE,
                        List.of(
                                windowRule("token-refresh:token:" + fingerprint(refreshToken), properties.tokenRefreshToken()),
                                windowRule("token-refresh:ip:" + normalizeIp(clientIp), properties.tokenRefreshIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkEmailResend(String email, String clientIp) {
        String emailFingerprint = fingerprint(normalize(email));
        acquireWithLogging(
                "email-resend",
                "emailFingerprint=" + emailFingerprint + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        EMAIL_MESSAGE,
                        List.of(
                                windowRule("email-resend:email-daily:" + emailFingerprint, properties.emailResend().emailDaily()),
                                windowRule("email-resend:ip-daily:" + normalizeIp(clientIp), properties.emailResend().ipDaily())
                        ),
                        List.of(
                                new RateLimitCooldownRule(
                                        "email-resend:email-cooldown:" + emailFingerprint,
                                        properties.emailResend().minimumInterval()
                                )
                        )
                )
        );
    }

    public void checkEmailVerify(String email, String clientIp) {
        String emailFingerprint = fingerprint(normalize(email));
        acquireWithLogging(
                "email-verify",
                "emailFingerprint=" + emailFingerprint + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        EMAIL_VERIFY_MESSAGE,
                        List.of(
                                windowRule("email-verify:email:" + emailFingerprint, properties.emailVerifyEmail()),
                                windowRule("email-verify:ip:" + normalizeIp(clientIp), properties.emailVerifyIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkPasswordResetRequest(String email, String clientIp) {
        String emailFingerprint = fingerprint(normalize(email));
        acquireWithLogging(
                "password-reset-request",
                "emailFingerprint=" + emailFingerprint + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        PASSWORD_RESET_MESSAGE,
                        List.of(
                                windowRule(
                                        "password-reset-request:email-daily:" + emailFingerprint,
                                        properties.passwordResetRequest().emailDaily()
                                ),
                                windowRule(
                                        "password-reset-request:ip-daily:" + normalizeIp(clientIp),
                                        properties.passwordResetRequest().ipDaily()
                                )
                        ),
                        List.of(
                                new RateLimitCooldownRule(
                                        "password-reset-request:email-cooldown:" + emailFingerprint,
                                        properties.passwordResetRequest().minimumInterval()
                                )
                        )
                )
        );
    }

    public void checkPasswordResetConfirm(String token, String clientIp) {
        acquireWithLogging(
                "password-reset-confirm",
                "tokenFingerprint=" + fingerprint(token) + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        PASSWORD_RESET_MESSAGE,
                        List.of(
                                windowRule(
                                        "password-reset-confirm:token:" + fingerprint(token),
                                        properties.passwordResetConfirmToken()
                                ),
                                windowRule(
                                        "password-reset-confirm:ip:" + normalizeIp(clientIp),
                                        properties.passwordResetConfirmIp()
                                )
                        ),
                        List.of()
                )
        );
    }

    public void checkPostReport(Long userId, String clientIp) {
        acquireWithLogging(
                "post-report",
                "userId=" + userId + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        DEFAULT_MESSAGE,
                        List.of(
                                windowRule("report:user:" + userId, properties.reportUser()),
                                windowRule("report:ip:" + normalizeIp(clientIp), properties.reportIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkMapImageLike(Long userId, String clientIp) {
        acquireWithLogging(
                "map-image-like",
                "userId=" + userId + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        DEFAULT_MESSAGE,
                        List.of(
                                windowRule("map-image-like:user:" + userId, properties.mapImageLikeUser()),
                                windowRule("map-image-like:ip:" + normalizeIp(clientIp), properties.mapImageLikeIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkRecommendationClick(Long userId, String clientIp) {
        acquireWithLogging(
                "recommendation-click",
                "userId=" + userId + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        DEFAULT_MESSAGE,
                        List.of(
                                windowRule("recommendation-click:user:" + userId, properties.recommendationClickUser()),
                                windowRule("recommendation-click:ip:" + normalizeIp(clientIp), properties.recommendationClickIp())
                        ),
                        List.of()
                )
        );
    }

    public void checkImageUpload(Long userId, String clientIp) {
        acquireWithLogging(
                "image-upload",
                "userId=" + userId + ", ip=" + normalizeIp(clientIp),
                () -> store.acquire(
                        DEFAULT_MESSAGE,
                        List.of(
                                windowRule("image-upload:user:" + userId, properties.imageUploadUser()),
                                windowRule("image-upload:ip:" + normalizeIp(clientIp), properties.imageUploadIp())
                        ),
                        List.of()
                )
        );
    }

    private void acquireWithLogging(String action, String subject, Runnable acquireAction) {
        try {
            acquireAction.run();
        } catch (RateLimitException exception) {
            log.warn("abuse rate limit exceeded. action={}, {}", action, subject);
            throw exception;
        }
    }

    private RateLimitWindowRule windowRule(String key, WindowPolicy policy) {
        return new RateLimitWindowRule(key, policy.limit(), policy.window());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private String normalizeIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
    }

    private String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = value != null ? value : "unknown";
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.", exception);
        }
    }
}
