package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewCreateRequest;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewUpdateRequest;
import com.typenull.pingdom.place.application.service.review.PlaceReviewService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/places/{placeId}/reviews")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.COMMON)
@Tag(name = SwaggerTagCatalog.PLACE_REVIEW)
public class PlaceReviewController {
    private final PlaceReviewService service;

    @PostMapping
    @AuthenticatedOnly
    @Operation(summary = "장소 리뷰 작성")
    public ResponseEntity<PlaceReviewResponse> create(@PathVariable Long placeId,
            @CurrentUser JwtAuthenticatedUser user, @Valid @RequestBody PlaceReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), placeId, request));
    }

    @GetMapping
    @Operation(summary = "장소 리뷰 목록 조회")
    public Page<PlaceReviewResponse> list(@PathVariable Long placeId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return service.list(placeId, page, limit);
    }

    @PatchMapping("/{reviewId}")
    @AuthenticatedOnly
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "본인 장소 리뷰 수정", description = "누락·null은 유지합니다. 사진 ID 배열은 최종 순서이며 []는 전체 제거입니다. 숨김은 유지하고 삭제 심사 중 수정은 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정된 리뷰. 기존 작성 응답과 동일한 계약"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: 값·개수·중복 오류. INVALID_REQUEST_BODY: JSON 형식·추천 코드 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PLACE_REVIEW_MEDIA_FORBIDDEN: 타인 또는 다른 장소 사진", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "리뷰가 없거나 다른 장소·타인·삭제된 리뷰: PLACE_REVIEW_NOT_FOUND. 사진 없음: PLACE_REVIEW_MEDIA_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "삭제 심사 중 또는 이미 연결·만료된 신규 사진", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"PLACE_REVIEW_UPDATE_PENDING_DELETION\",\"message\":\"삭제 심사 중인 리뷰는 수정할 수 없습니다.\"}")))
    })
    public PlaceReviewResponse update(@PathVariable Long placeId, @PathVariable Long reviewId,
            @CurrentUser JwtAuthenticatedUser user,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = "{\"content\":\"안내가 친절합니다.\",\"recommendReasons\":[\"FRIENDLY\",\"CLEAN\"],\"reviewMediaIds\":[12,9]}")))
            @Valid @RequestBody PlaceReviewUpdateRequest request) {
        return service.update(user.userId(), placeId, reviewId, request);
    }

    @DeleteMapping("/{reviewId}")
    @AuthenticatedOnly
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "본인 장소 리뷰 삭제", description = "본인 리뷰를 논리 삭제합니다. 숨김·심사 중·이미 삭제된 본인 리뷰도 204입니다. 방문 인증·체크인 및 심사 증거는 보존하며 재작성은 기존 작성 정책을 따릅니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 완료 또는 본인이 이미 삭제한 리뷰", content = @Content),
            @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PLACE_REVIEW_NOT_FOUND: 없는 리뷰·다른 장소·타인 리뷰", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long placeId, @PathVariable Long reviewId,
            @CurrentUser JwtAuthenticatedUser user) {
        service.delete(user.userId(), placeId, reviewId);
        return ResponseEntity.noContent().build();
    }
}
