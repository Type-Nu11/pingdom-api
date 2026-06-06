package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.api.dto.ban.BanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.application.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/users")
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
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminUserService.listBannedUsers(pageable);
    }

    @PostMapping("/ban/{userId}")
    @Operation(
            summary = "사용자 밴 처리",
            description = "관리자가 특정 사용자를 밴 처리합니다. 요청 본문은 선택이며, 전달 시 밴 사유를 함께 저장합니다."
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
            @RequestBody(required = false) BanRequest request
    ) {
        String reason = request == null ? null : request.reason();
        return adminUserService.banUser(userId, reason);
    }
}
