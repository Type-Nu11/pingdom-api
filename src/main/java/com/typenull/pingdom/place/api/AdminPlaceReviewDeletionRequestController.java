package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.review.AdminPlaceReviewDeletionRequestPageResponse;
import com.typenull.pingdom.place.api.dto.review.AdminPlaceReviewDeletionRequestResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestReviewRequest;
import com.typenull.pingdom.place.application.service.review.AdminPlaceReviewDeletionRequestService;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/place-review-deletion-requests")
@RequiredArgsConstructor
@Validated
@AdminOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminPlaceReviewDeletionRequestController {

    private final AdminPlaceReviewDeletionRequestService deletionRequestService;

    @GetMapping
    @Operation(summary = "리뷰 삭제 신청 목록 조회")
    public AdminPlaceReviewDeletionRequestPageResponse list(
            @RequestParam(required = false) PlaceReviewDeletionRequestStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return deletionRequestService.list(admin.userId(), status, page, limit);
    }

    @GetMapping("/{deletionRequestId}")
    @Operation(summary = "리뷰 삭제 신청 상세 조회")
    public AdminPlaceReviewDeletionRequestResponse get(
            @PathVariable Long deletionRequestId,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return deletionRequestService.get(admin.userId(), deletionRequestId);
    }

    @PostMapping("/{deletionRequestId}/review")
    @Operation(summary = "리뷰 삭제 신청 심사")
    public AdminPlaceReviewDeletionRequestResponse review(
            @PathVariable Long deletionRequestId,
            @Valid @RequestBody PlaceReviewDeletionRequestReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return deletionRequestService.review(admin.userId(), deletionRequestId, request);
    }
}
