package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewMediaUploadResponse;
import com.typenull.pingdom.place.application.service.review.PlaceReviewMediaService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 리뷰 작성 전 사진을 임시 업로드하거나 취소하는 인증 API입니다.
 * 장소·현재 사용자 ID를 함께 넘기며 리뷰 연결과 소유권·만료 검사는 미디어 서비스가 담당합니다.
 */
@RestController
@RequestMapping("/places/{placeId}/reviews/media")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.COMMON)
@Tag(name = SwaggerTagCatalog.REVIEW_MEDIA)
public class PlaceReviewMediaController {

    private final PlaceReviewMediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "장소 리뷰 사진 업로드",
            description = "리뷰 작성 전에 JPEG 또는 PNG 사진 한 개를 업로드합니다. 업로드된 사진은 24시간 안에 본인의 같은 장소 리뷰에 한 번만 연결할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "리뷰 사진 업로드 완료"),
            @ApiResponse(responseCode = "400", description = "비어 있거나 손상된 파일", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "파일 크기 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "지원하지 않는 파일 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "리뷰 사진 저장소 사용 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PlaceReviewMediaUploadResponse> upload(
            @PathVariable Long placeId,
            @RequestPart("file") MultipartFile file,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.upload(user.userId(), placeId, file));
    }

    @DeleteMapping("/{reviewMediaId}")
    @Operation(summary = "장소 리뷰 사진 업로드 취소", description = "리뷰에 연결하지 않은 본인의 사진 업로드를 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "리뷰 사진 업로드 취소 완료"),
            @ApiResponse(responseCode = "403", description = "다른 사용자의 사진", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "리뷰 사진을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 리뷰에 연결된 사진", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancel(
            @PathVariable Long placeId,
            @PathVariable Long reviewMediaId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        mediaService.cancel(user.userId(), placeId, reviewMediaId);
        return ResponseEntity.noContent().build();
    }
}
