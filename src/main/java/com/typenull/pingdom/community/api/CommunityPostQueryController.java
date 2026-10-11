package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityCountryOption;
import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.application.CommunityPostQueryService;
import com.typenull.pingdom.community.domain.CommunityCountry;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
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

    @GetMapping("/posts")
    @Operation(summary = "전체 커뮤니티 게시글 목록 조회", description = "카테고리 구분 없이 최신순으로 조회합니다. page·limit과 country는 카테고리 목록과 같은 의미입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전체 게시글 목록", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "국가 또는 페이지 요청 오류", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class})))
    })
    public ResponseEntity<CommunityPostListResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(description = "작성자 국가. 생략 시 전체", schema = @Schema(allowableValues = {"KR", "US", "JP", "VN", "CN", "TH"}))
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(communityPostQueryService.findAll(page, limit, country));
    }

    @GetMapping("/countries")
    @Operation(summary = "커뮤니티 국가 필터 목록", description = "국가 미상 회원의 글은 국가 필터를 생략한 목록에서 조회할 수 있습니다.")
    public ResponseEntity<List<CommunityCountryOption>> countries() {
        return ResponseEntity.ok(Arrays.stream(CommunityCountry.values())
                .map(country -> new CommunityCountryOption(country.name(), country.getDisplayName())).toList());
    }

    @GetMapping("/categories/{categoryId}/posts")
    @Operation(summary = "카테고리별 커뮤니티 게시글 목록 조회", description = "최신 등록순으로 게시글 미리보기·사진·지역·통계·카테고리를 조회합니다. 국가 필터는 작성자의 국가를 기준으로 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카테고리별 게시글 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "카테고리 또는 페이지 요청값이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class})))
    })
    public ResponseEntity<CommunityPostListResponse> findByCategory(
            @PathVariable String categoryId,
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기. 최대 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(description = "작성자 국가. 생략 시 전체", schema = @Schema(allowableValues = {"KR", "US", "JP", "VN", "CN", "TH"}))
            @RequestParam(required = false) String country
    ) {
        return ResponseEntity.ok(communityPostQueryService.findByCategory(categoryId, page, limit, country));
    }
}
