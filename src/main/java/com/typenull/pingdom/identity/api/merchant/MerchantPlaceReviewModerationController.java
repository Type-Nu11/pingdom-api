package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewDeletionRequestResponse;
import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewPageResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestCreateRequest;
import com.typenull.pingdom.place.application.service.review.MerchantPlaceReviewModerationService;
import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/places/{placeId}/reviews")
@RequiredArgsConstructor
@Validated
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceReviewModerationController {

    private final MerchantPlaceReviewModerationService reviewModerationService;

    @GetMapping
    @Operation(
            operationId = "listMerchantPlaceReviews",
            summary = "점주 리뷰 관리 목록 조회",
            description = "소유 장소의 VISIBLE, HIDDEN, DELETED 리뷰와 최신 삭제 신청 상태를 1-based 페이지로 조회합니다."
    )
    public MerchantPlaceReviewPageResponse list(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return reviewModerationService.list(user.userId(), placeId, page, limit);
    }

    @PostMapping("/{reviewId}/deletion-requests")
    @Operation(summary = "점주 리뷰 숨김 및 삭제 신청")
    public ResponseEntity<MerchantPlaceReviewDeletionRequestResponse> requestDeletion(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @PathVariable Long reviewId,
            @Valid @RequestBody PlaceReviewDeletionRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewModerationService.requestDeletion(
                user.userId(),
                placeId,
                reviewId,
                request
        ));
    }
}
