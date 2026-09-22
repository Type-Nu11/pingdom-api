package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityReportCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityReportCreateResponse;
import com.typenull.pingdom.community.application.CommunityReportService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts/{postId}")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.REPORT)
@SecurityRequirement(name = "bearerAuth")
public class CommunityReportController {

    private final CommunityReportService communityReportService;

    @PostMapping("/reports")
    @Operation(summary = "커뮤니티 게시글 신고", description = "동일 사용자·대상은 처리 상태와 관계없이 한 번만 신고할 수 있습니다. 접수만으로 대상이 숨겨지지는 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "신고 사유 또는 설명이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "신고 대상이 없거나 게시글에 속하지 않는 댓글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 신고한 대상", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommunityReportCreateResponse> reportPost(
            @PathVariable long postId,
            @Valid @RequestBody CommunityReportCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser authenticatedUser = JwtAuthenticatedUser.require(
                user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityReportService.reportPost(postId, authenticatedUser.userId(), request));
    }

    @PostMapping("/comments/{commentId}/reports")
    @Operation(summary = "커뮤니티 댓글 신고", description = "동일 사용자·대상은 처리 상태와 관계없이 한 번만 신고할 수 있습니다. 접수만으로 대상이 숨겨지지는 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "신고 사유 또는 설명이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "신고 대상이 없거나 게시글에 속하지 않는 댓글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 신고한 대상", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommunityReportCreateResponse> reportComment(
            @PathVariable long postId,
            @PathVariable long commentId,
            @Valid @RequestBody CommunityReportCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser authenticatedUser = JwtAuthenticatedUser.require(
                user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityReportService.reportComment(postId, commentId, authenticatedUser.userId(), request));
    }
}
