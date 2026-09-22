package com.typenull.pingdom.identity.application.service.withdrawal;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 탈퇴 회원의 최종 삭제 보존 기간과 한 번에 삭제할 회원 수입니다.
 * 미설정 값은 30일·100명이며 기간의 양수 여부를 이 record에서 별도로 검증하지는 않습니다.
 */
@Validated
@ConfigurationProperties(prefix = "user.withdrawal")
public record UserWithdrawalProperties(
        Duration retention,
        @Min(1) Integer cleanupBatchSize
) {

    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;

    public UserWithdrawalProperties {
        if (retention == null) {
            retention = DEFAULT_RETENTION;
        }
        if (cleanupBatchSize == null) {
            cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;
        }
    }
}
