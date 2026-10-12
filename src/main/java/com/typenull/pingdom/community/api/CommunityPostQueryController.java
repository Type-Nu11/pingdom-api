package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.application.CommunityPostQueryService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
public class CommunityPostQueryController {

    private final CommunityPostQueryService communityPostQueryService;

    @GetMapping("/categories/{categoryId}/posts")
    @Operation(summary = "카테고리별 커뮤니티 게시글 목록 조회", description = "미리보기·사진·지역·생성 시각·조회/좋아요/댓글 수와 단일 카테고리를 최신순으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카테고리별 게시글 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "카테고리 또는 페이지 요청값이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class})))
    })
    public ResponseEntity<CommunityPostListResponse> findByCategory(
            @PathVariable String categoryId,
            @Parameter(description = "ISO alpha-2 국가 코드. 허용 값은 GET /community/countries")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기. 최대 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(communityPostQueryService.findPosts(categoryId, countryCode, page, limit));
    }

    @GetMapping("/posts")
    @Operation(summary = "전체 커뮤니티 게시글 목록", description = "최신순. 국가 미설정 게시글은 전체 조회에만 포함되며 국가 필터로 추론하지 않습니다. 단일 카테고리를 유지합니다.")
    @ApiResponse(responseCode="400", description="INVALID_COUNTRY 또는 페이지 범위 오류")
    public ResponseEntity<CommunityPostListResponse> findAll(
            @RequestParam(required=false) String countryCode,
            @RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(communityPostQueryService.findPosts(null, countryCode, page, limit));
    }
    @GetMapping("/countries") @Operation(summary = "커뮤니티 국가 필터 허용 코드")
    public java.util.List<String> countries() {
        return com.typenull.pingdom.community.application.CommunityCountryCodes.ALL;
    }
}
