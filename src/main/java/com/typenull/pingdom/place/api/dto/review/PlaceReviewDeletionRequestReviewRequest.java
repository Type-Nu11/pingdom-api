package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 리뷰 삭제 요청의 관리자 결정과 선택적인 심사 메모입니다.
 * APPROVED·REJECTED 제한과 거절 시 필수 메모는 도메인 심사 메서드에서 확인합니다.
 */
public record PlaceReviewDeletionRequestReviewRequest(
        @NotNull PlaceReviewDeletionRequestStatus decision,
        @Size(max = 500) String reviewNote
) {}
