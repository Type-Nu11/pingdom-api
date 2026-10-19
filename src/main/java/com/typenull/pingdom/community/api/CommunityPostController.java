package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityPostCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCreateResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostUpdateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostUpdateResponse;
import com.typenull.pingdom.community.application.CommunityPostCommandService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/community/posts")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
public class CommunityPostController {

    private final CommunityPostCommandService communityPostCommandService;

    @PostMapping
    @Operation(summary = "커뮤니티 게시글 등록", description = "인증된 사용자가 카테고리, 제목, 본문, 연결 장소 ID 목록으로 게시글을 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "커뮤니티 게시글 등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "카테고리, 입력값 또는 장소 선택이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "연결할 장소를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommunityPostCreateResponse> create(
            @Valid @RequestBody CommunityPostCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser authenticatedUser = JwtAuthenticatedUser.require(
                user,
                () -> new AuthException(AuthErrorCode.INVALID_TOKEN)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityPostCommandService.create(authenticatedUser.userId(), request));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "본인 커뮤니티 게시글 부분 수정", description = "공개된 본인 글만 수정합니다. 누락 필드는 유지하고 countryCode/region의 null은 초기화합니다. 다른 필드의 null과 빈 요청은 400입니다. placeIds/imageIds는 최종 전체 목록이며 []는 제거합니다. 같은 글의 기존 사진과 본인 미사용·미만료 업로드 ID를 순서대로 지정합니다. 사진은 최대 10장, 단일 카테고리 및 기존 작성 제약을 유지합니다. 실패 시 글·장소·사진 변경은 롤백됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공. postId와 updatedAt 반환", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력 제약, INVALID_CATEGORY/COUNTRY/IMAGE, DUPLICATE_PLACE 또는 PLACE_REQUIRED", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "JWT 없음 또는 무효", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND: 타인·숨김·삭제·없는 글, PLACE_NOT_FOUND: 연결 장소 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommunityPostUpdateResponse> update(
            @PathVariable long postId, @Valid @RequestBody CommunityPostUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser authenticated = JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN));
        return ResponseEntity.ok(communityPostCommandService.update(authenticated.userId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "본인 커뮤니티 게시글 삭제", description = "본인 글은 숨김 상태에서도 삭제할 수 있습니다. 삭제 후 공개/내 목록·상세·댓글·좋아요 접근은 불가하며 반복 삭제는 404입니다. 댓글·좋아요·신고 이력은 보존하고 장소 연결은 제거합니다. 사진은 DB 삭제와 S3 삭제 Outbox를 함께 기록하며 실제 파일 삭제는 비동기입니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "JWT 없음 또는 무효", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND: 타인·이미 삭제·없는 글", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable long postId, @CurrentUser JwtAuthenticatedUser user) {
        JwtAuthenticatedUser authenticated = JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN));
        communityPostCommandService.delete(authenticated.userId(), postId);
        return ResponseEntity.noContent().build();
    }
}
