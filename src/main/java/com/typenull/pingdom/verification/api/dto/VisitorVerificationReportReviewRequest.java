package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 방문 제보 심사 결정과 선택 메모. 미심사 상태 결정은 금지하고 거절에는 도메인에서 사유를 요구. */
public record VisitorVerificationReportReviewRequest(
        @NotNull VisitorVerificationReportStatus decision,
        @Size(max = 500) String reviewNote
) {}
