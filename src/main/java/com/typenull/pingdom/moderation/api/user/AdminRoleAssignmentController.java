package com.typenull.pingdom.moderation.api.user;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAssignmentService;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentRequest;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentResponse;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users/{userId}/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminRoleAssignmentController {

    private final AdminRoleAssignmentService roleAssignmentService;

    @GetMapping
    @Operation(summary = "관리자 역할 이력 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = AdminRoleAssignmentResponse[].class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 역할 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "대상 관리자 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<AdminRoleAssignmentResponse> list(
            @PathVariable Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return roleAssignmentService.list(admin.userId(), userId);
    }

    @PostMapping
    @Operation(summary = "관리자 역할 부여")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "부여 성공", content = @Content(schema = @Schema(implementation = AdminRoleAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "역할 할당 요청 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 역할 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "대상 관리자를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 활성 역할이 존재함", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminRoleAssignmentResponse assign(
            @PathVariable Long userId,
            @Valid @RequestBody AdminRoleAssignmentRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return roleAssignmentService.assign(admin.userId(), userId, request);
    }

    @DeleteMapping("/{role}")
    @Operation(summary = "관리자 역할 회수")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회수 성공", content = @Content(schema = @Schema(implementation = AdminRoleAssignmentResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 역할 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "활성 역할을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminRoleAssignmentResponse revoke(
            @PathVariable Long userId,
            @PathVariable AdminRole role,
            @Valid @RequestBody(required = false) AdminRoleAssignmentRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return roleAssignmentService.revoke(
                admin.userId(),
                userId,
                role,
                request == null ? null : request.reason()
        );
    }
}
