package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.api.dto.ban.BanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserDetailResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionStatusResponse;
import com.typenull.pingdom.moderation.application.AdminUserService;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/users/banned")
    @Operation(
            summary = "밴 유저 목록 조회",
            description = "관리자가 밴 처리된 사용자 목록을 페이지 단위로 조회합니다. limit 값은 내부적으로 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "밴 유저 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminBannedUserResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "users": [
                                                {
                                                  "userId": 7,
                                                  "username": "blockedUser01",
                                                  "banned": true
                                                }
                                              ],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 1,
                                              "totalPages": 1,
                                              "hasNext": false
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminBannedUserResponse listBannedUsers(
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        Pageable normalizedPageable = PageRequest.of(Math.max(page - 1, 0), limit);
        return adminUserService.listBannedUsers(normalizedPageable);
    }

    @GetMapping("/users/banned/{userId}")
    @Operation(
            summary = "밴 유저 상세 조회",
            description = "관리자가 특정 밴 유저의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "밴 유저 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminBannedUserDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "userId": 7,
                                              "username": "blockedUser01",
                                              "email": "blockedUser01@example.com",
                                              "birthYear": 1998,
                                              "language": "ko",
                                              "country": "KR",
                                              "role": "USER",
                                              "banned": true,
                                              "bannedAt": "2026-06-07T13:30:00",
                                              "banReason": "반복적인 신고 누적",
                                              "createdAt": "2026-06-01T10:00:00"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "밴 유저를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사용자를 찾을 수 없습니다.",
                                              "code": "USER_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminBannedUserDetailResponse getBannedUser(
            @Parameter(description = "조회할 밴 유저 ID", example = "7") @PathVariable Long userId
    ) {
        return adminUserService.getBannedUser(userId);
    }

    @GetMapping("/users/{userId}/sanction")
    @Operation(
            summary = "사용자 제재 상태 조회",
            description = "관리자가 특정 사용자의 현재 제재 상태를 조회합니다. 만료된 기간 밴은 현재 제재로 보지 않습니다."
    )
    public AdminUserSanctionStatusResponse getUserSanctionStatus(
            @Parameter(description = "조회할 사용자 ID", example = "7") @PathVariable Long userId
    ) {
        return adminUserService.getUserSanctionStatus(userId);
    }

    @GetMapping("/users/{userId}/sanctions")
    @Operation(
            summary = "사용자 제재 이력 조회",
            description = "관리자가 특정 사용자의 제재 변경 이력을 페이지 단위로 조회합니다."
    )
    public AdminUserSanctionHistoryResponse listUserSanctionHistories(
            @Parameter(description = "조회할 사용자 ID", example = "7") @PathVariable Long userId,
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "제재 유형 필터", example = "TEMPORARY")
            @RequestParam(required = false) UserBanType banType,
            @Parameter(description = "처리 상태 필터", example = "APPLIED")
            @RequestParam(required = false) UserSanctionAction action,
            @Parameter(description = "처리 시각 시작 필터", example = "2026-06-01T00:00:00")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "처리 시각 종료 필터", example = "2026-06-30T23:59:59")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to
    ) {
        Pageable normalizedPageable = PageRequest.of(Math.max(page - 1, 0), limit);
        return adminUserService.listUserSanctionHistories(userId, banType, action, from, to, normalizedPageable);
    }

    @PostMapping("/ban/{userId}")
    @Operation(
            summary = "사용자 밴 처리",
            description = "관리자가 특정 사용자를 밴 처리합니다. expiresAt 또는 durationDays를 전달하면 기간 밴으로, 둘 다 비우면 영구 밴으로 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "밴 처리 성공",
                    content = @Content(schema = @Schema(implementation = BanResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사용자를 찾을 수 없습니다.",
                                              "code": "USER_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public BanResponse banUser(
            @Parameter(description = "밴 처리할 사용자 ID", example = "7") @PathVariable Long userId,
            @Valid @RequestBody(required = false) BanRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        return adminUserService.banUser(userId, request, adminUser.userId());
    }

    @PostMapping("/ban/{userId}/release")
    @Operation(
            summary = "사용자 밴 해제",
            description = "관리자가 영구 밴 또는 기간 밴 상태의 사용자를 명시적으로 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "밴 해제 성공",
                    content = @Content(schema = @Schema(implementation = UnbanResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "제재 중인 사용자가 아님",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "제재 중인 사용자가 아닙니다.",
                                              "code": "USER_NOT_BANNED"
                                            }
                                            """
                            )
                    )
            )
    })
    public UnbanResponse unbanUser(
            @Parameter(description = "밴 해제할 사용자 ID", example = "7") @PathVariable Long userId,
            @Valid @RequestBody(required = false) UnbanRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        return adminUserService.unbanUser(userId, request, adminUser.userId());
    }
}
