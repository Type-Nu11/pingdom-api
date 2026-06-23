package com.typenull.pingdom.shared.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "abuse.rate-limit")
public record AbuseRateLimitProperties(
        @Valid WindowPolicy loginUsername,
        @Valid WindowPolicy loginIp,
        @Valid WindowPolicy tokenRefreshToken,
        @Valid WindowPolicy tokenRefreshIp,
        @Valid EmailResendPolicy emailResend,
        @Valid WindowPolicy reportUser,
        @Valid WindowPolicy reportIp,
        @Valid WindowPolicy mapImageLikeUser,
        @Valid WindowPolicy mapImageLikeIp,
        @Valid WindowPolicy recommendationClickUser,
        @Valid WindowPolicy recommendationClickIp,
        @Valid WindowPolicy imageUploadUser,
        @Valid WindowPolicy imageUploadIp,
        @Min(1) Integer maxKeys
) {

    private static final int DEFAULT_MAX_KEYS = 100_000;

    public AbuseRateLimitProperties {
        loginUsername = WindowPolicy.withDefaults(loginUsername, 5, Duration.ofMinutes(1));
        loginIp = WindowPolicy.withDefaults(loginIp, 120, Duration.ofMinutes(1));
        tokenRefreshToken = WindowPolicy.withDefaults(tokenRefreshToken, 10, Duration.ofMinutes(1));
        tokenRefreshIp = WindowPolicy.withDefaults(tokenRefreshIp, 240, Duration.ofMinutes(1));
        emailResend = EmailResendPolicy.withDefaults(emailResend);
        reportUser = WindowPolicy.withDefaults(reportUser, 10, Duration.ofHours(1));
        reportIp = WindowPolicy.withDefaults(reportIp, 200, Duration.ofHours(1));
        mapImageLikeUser = WindowPolicy.withDefaults(mapImageLikeUser, 60, Duration.ofMinutes(1));
        mapImageLikeIp = WindowPolicy.withDefaults(mapImageLikeIp, 600, Duration.ofMinutes(1));
        recommendationClickUser = WindowPolicy.withDefaults(recommendationClickUser, 120, Duration.ofMinutes(1));
        recommendationClickIp = WindowPolicy.withDefaults(recommendationClickIp, 1_000, Duration.ofMinutes(1));
        imageUploadUser = WindowPolicy.withDefaults(imageUploadUser, 10, Duration.ofHours(1));
        imageUploadIp = WindowPolicy.withDefaults(imageUploadIp, 100, Duration.ofHours(1));
        if (maxKeys == null) {
            maxKeys = DEFAULT_MAX_KEYS;
        }
    }

    public record WindowPolicy(
            @Min(1) Integer limit,
            Duration window
    ) {

        public WindowPolicy {
            if (window != null && !window.isPositive()) {
                throw new IllegalArgumentException("rate limit window는 양수여야 합니다.");
            }
        }

        static WindowPolicy withDefaults(WindowPolicy policy, int defaultLimit, Duration defaultWindow) {
            if (policy == null) {
                return new WindowPolicy(defaultLimit, defaultWindow);
            }
            Integer resolvedLimit = policy.limit() == null ? defaultLimit : policy.limit();
            Duration resolvedWindow = policy.window() == null ? defaultWindow : policy.window();
            return new WindowPolicy(resolvedLimit, resolvedWindow);
        }
    }

    public record EmailResendPolicy(
            Duration minimumInterval,
            @Valid WindowPolicy emailDaily,
            @Valid WindowPolicy ipDaily
    ) {

        private static final Duration DEFAULT_MINIMUM_INTERVAL = Duration.ofMinutes(1);

        public EmailResendPolicy {
            if (minimumInterval != null && !minimumInterval.isPositive()) {
                throw new IllegalArgumentException("email resend minimum interval은 양수여야 합니다.");
            }
        }

        static EmailResendPolicy withDefaults(EmailResendPolicy policy) {
            if (policy == null) {
                return new EmailResendPolicy(
                        DEFAULT_MINIMUM_INTERVAL,
                        new WindowPolicy(5, Duration.ofDays(1)),
                        new WindowPolicy(100, Duration.ofDays(1))
                );
            }
            Duration resolvedMinimumInterval = policy.minimumInterval() == null
                    ? DEFAULT_MINIMUM_INTERVAL
                    : policy.minimumInterval();
            WindowPolicy resolvedEmailDaily = WindowPolicy.withDefaults(
                    policy.emailDaily(),
                    5,
                    Duration.ofDays(1)
            );
            WindowPolicy resolvedIpDaily = WindowPolicy.withDefaults(
                    policy.ipDaily(),
                    100,
                    Duration.ofDays(1)
            );
            return new EmailResendPolicy(resolvedMinimumInterval, resolvedEmailDaily, resolvedIpDaily);
        }
    }
}
