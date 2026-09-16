package com.typenull.pingdom.moderation.api.user;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAssignmentTargetQueryService;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentTargetSearchResponse;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users/role-targets")
@RequiredArgsConstructor
@AdminOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminRoleAssignmentTargetController {

    private final AdminRoleAssignmentTargetQueryService queryService;

    @GetMapping
    @Operation(
            summary = "관리자 역할 부여 대상 사용자 검색",
            description = "관리자 역할 API에 전달할 수 있는 관리자 계정만 username 부분 일치로 검색합니다. 사용자 공통 표시 이름은 제공되지 않아 username만 검색하며, 빈 keyword는 전체 대상 조회로 처리합니다. page는 1부터 시작하고 limit은 1~100으로 보정됩니다. 이메일과 전화번호는 반환하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공", content = @Content(schema = @Schema(implementation = AdminRoleAssignmentTargetSearchResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 역할 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminRoleAssignmentTargetSearchResponse search(
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "username 부분 일치 검색어. 비어 있으면 전체 역할 부여 대상을 조회합니다.", example = "operator")
            @RequestParam(required = false) String keyword,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return queryService.search(admin.userId(), keyword, page, limit);
    }
}
