package com.typenull.pingdom.moderation.api.dto.trust;

/** 이번 실행에서 실제 커밋한 정책 수와 그중 점수가 변경된 정책 수. */
public record AdminTrustScoreBatchResponse(int processedCount, int changedCount) {
}
