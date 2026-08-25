package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewDeletionRequestResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestCreateRequest;
import com.typenull.pingdom.place.application.service.review.MerchantPlaceReviewModerationService;
import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/places/{placeId}/reviews")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceReviewModerationController {

    private final MerchantPlaceReviewModerationService reviewModerationService;

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
