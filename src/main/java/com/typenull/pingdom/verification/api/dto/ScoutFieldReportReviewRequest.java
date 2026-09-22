package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 현장 제보의 심사 결과와 메모다. SUBMITTED 결정 금지 및 거절 사유 필수 조건은 도메인이 확인한다. */
public record ScoutFieldReportReviewRequest(
        @NotNull ScoutFieldReportStatus decision,
        @Size(max = 500) String reviewNote
) {}
