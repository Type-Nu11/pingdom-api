package com.typenull.pingdom.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 실패/보존 보류를 다음 후보 조회에서 제외하기 위한 상태이며 개인정보나 예외 원문은 저장하지 않는다. */
@Entity
@Table(name = "user_purge_retry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPurgeRetry {

    public enum Reason { RETAINED_REFERENCE, PROCESSING_FAILED }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 32)
    private Reason reason;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static UserPurgeRetry defer(Long userId, Reason reason, LocalDateTime now, LocalDateTime nextAttemptAt) {
        UserPurgeRetry retry = new UserPurgeRetry();
        retry.userId = userId;
        retry.reason = reason;
        retry.updatedAt = now;
        retry.nextAttemptAt = nextAttemptAt;
        return retry;
    }
}
