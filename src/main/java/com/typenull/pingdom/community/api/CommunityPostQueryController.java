package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.application.CommunityPostQueryService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/community/categories")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class CommunityPostQueryController {

    private final CommunityPostQueryService communityPostQueryService;

    @GetMapping("/{categoryId}/posts")
    @Operation(summary = "카테고리별 커뮤니티 게시글 목록 조회", description = "최신 등록순으로 게시글 ID와 제목만 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카테고리별 게시글 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "카테고리 또는 페이지 요청값이 올바르지 않음", useReturnTypeSchema = true)
    })
    public ResponseEntity<CommunityPostListResponse> findByCategory(
            @PathVariable String categoryId,
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기. 최대 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(communityPostQueryService.findByCategory(categoryId, page, limit));
    }
}
