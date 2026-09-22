package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityPostCategoryListResponse;
import com.typenull.pingdom.community.application.CommunityPostCategoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/categories")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
public class CommunityPostCategoryController {

    private final CommunityPostCategoryQueryService communityPostCategoryQueryService;

    @GetMapping
    @Operation(summary = "커뮤니티 게시글 카테고리 조회", description = "게시글 작성과 카테고리별 조회에 사용할 활성 카테고리의 식별자와 표시 이름을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커뮤니티 게시글 카테고리 조회 성공", useReturnTypeSchema = true)
    })
    public ResponseEntity<CommunityPostCategoryListResponse> findCategories() {
        return ResponseEntity.ok(communityPostCategoryQueryService.findCategories());
    }
}
