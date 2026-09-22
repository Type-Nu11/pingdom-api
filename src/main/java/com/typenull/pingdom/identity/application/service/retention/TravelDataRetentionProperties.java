package com.typenull.pingdom.identity.application.service.retention;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 탈퇴 회원의 여행 정보 보존 기간과 회원 조회 배치 크기를 설정.
 * 미설정 시 7일·100명을 사용하며 만료 활동 의도 전체 삭제 건수는 이 배치 크기의 제한 대상에서 제외.
 */
@Validated
@ConfigurationProperties(prefix = "travel.data-retention")
public record TravelDataRetentionProperties(
        Duration withdrawnUserRetention,
        @Min(1) Integer cleanupBatchSize
) {

    private static final Duration DEFAULT_WITHDRAWN_USER_RETENTION = Duration.ofDays(7);
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;

    public TravelDataRetentionProperties {
        if (withdrawnUserRetention == null) {
            withdrawnUserRetention = DEFAULT_WITHDRAWN_USER_RETENTION;
        }
        if (cleanupBatchSize == null) {
            cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;
        }
    }
}
