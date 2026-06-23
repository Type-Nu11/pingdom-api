package com.typenull.pingdom.shared.ratelimit;

import com.typenull.pingdom.shared.ratelimit.AbuseRateLimitProperties.WindowPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AbuseRateLimitService {

    private static final String DEFAULT_MESSAGE = "요청 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String EMAIL_MESSAGE = "인증 메일 재발송 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";

    private final AbuseRateLimitProperties properties;
    private final RateLimitStore store;

    public AbuseRateLimitService(AbuseRateLimitProperties properties, RateLimitStore store) {
        this.properties = properties;
        this.store = store;
    }

    public void checkLogin(String username, String clientIp) {
        store.acquire(
                DEFAULT_MESSAGE,
                List.of(
                        windowRule("login:username:" + fingerprint(normalize(username)), properties.loginUsername()),
                        windowRule("login:ip:" + normalizeIp(clientIp), properties.loginIp())
                ),
                List.of()
        );
    }

    public void checkTokenRefresh(String refreshToken, String clientIp) {
        store.acquire(
                DEFAULT_MESSAGE,
                List.of(
                        windowRule("token-refresh:token:" + fingerprint(refreshToken), properties.tokenRefreshToken()),
                        windowRule("token-refresh:ip:" + normalizeIp(clientIp), properties.tokenRefreshIp())
                ),
                List.of()
        );
    }

    public void checkEmailResend(String email, String clientIp) {
        String emailFingerprint = fingerprint(normalize(email));
        store.acquire(
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
        );
    }

    public void checkPostReport(Long userId, String clientIp) {
        store.acquire(
                DEFAULT_MESSAGE,
                List.of(
                        windowRule("report:user:" + userId, properties.reportUser()),
                        windowRule("report:ip:" + normalizeIp(clientIp), properties.reportIp())
                ),
                List.of()
        );
    }

    public void checkMapImageLike(Long userId, String clientIp) {
        store.acquire(
                DEFAULT_MESSAGE,
                List.of(
                        windowRule("map-image-like:user:" + userId, properties.mapImageLikeUser()),
                        windowRule("map-image-like:ip:" + normalizeIp(clientIp), properties.mapImageLikeIp())
                ),
                List.of()
        );
    }

    public void checkRecommendationClick(Long userId, String clientIp) {
        store.acquire(
                DEFAULT_MESSAGE,
                List.of(
                        windowRule("recommendation-click:user:" + userId, properties.recommendationClickUser()),
                        windowRule("recommendation-click:ip:" + normalizeIp(clientIp), properties.recommendationClickIp())
                ),
                List.of()
        );
    }

    public void checkImageUpload(Long userId, String clientIp) {
        store.acquire(
                DEFAULT_MESSAGE,
                List.of(
                        windowRule("image-upload:user:" + userId, properties.imageUploadUser()),
                        windowRule("image-upload:ip:" + normalizeIp(clientIp), properties.imageUploadIp())
                ),
                List.of()
        );
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
            return HexFormat.of().formatHex(digest.digest(normalize(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.", exception);
        }
    }
}
