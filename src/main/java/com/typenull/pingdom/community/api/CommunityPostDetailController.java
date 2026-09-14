package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityPostDetailResponse;
import com.typenull.pingdom.community.application.CommunityPostQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class CommunityPostDetailController {

    private final CommunityPostQueryService communityPostQueryService;

    @GetMapping("/{postId}")
    @Operation(summary = "커뮤니티 게시글 상세 조회", description = "게시글 제목, 본문, 연결 장소의 현재 표시 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커뮤니티 게시글 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음", useReturnTypeSchema = true)
    })
    public ResponseEntity<CommunityPostDetailResponse> findDetail(@PathVariable long postId) {
        return ResponseEntity.ok(communityPostQueryService.findDetail(postId));
    }
}
