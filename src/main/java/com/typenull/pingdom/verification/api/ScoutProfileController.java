package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.ScoutProfileRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileResponse;
import com.typenull.pingdom.verification.application.ScoutProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/scout-profile")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class ScoutProfileController {

    private final ScoutProfileService service;

    @PostMapping
    @Operation(
            summary = "Scout 프로필 신청",
            description = """
                    활성 일반 사용자만 Scout 프로필을 신청할 수 있습니다.
                    PENDING, ACTIVE, SUSPENDED, REVOKED를 포함해 기존 Scout 프로필이 하나라도 있으면
                    재신청할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Scout 프로필 신청 성공"),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아님 (SCOUT_PROFILE_ACCOUNT_REQUIRED)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "SCOUT_PROFILE_ACCOUNT_REQUIRED",
                                    value = """
                                            {"message":"활성 일반 사용자만 Scout 프로필을 신청할 수 있습니다.","code":"SCOUT_PROFILE_ACCOUNT_REQUIRED"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 Scout 프로필 (SCOUT_PROFILE_ALREADY_EXISTS)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "SCOUT_PROFILE_ALREADY_EXISTS",
                                    value = """
                                            {"message":"Scout 프로필이 이미 존재합니다.","code":"SCOUT_PROFILE_ALREADY_EXISTS"}
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<ScoutProfileResponse> apply(
            @Valid @RequestBody ScoutProfileRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(user.userId(), request));
    }

    @GetMapping
    @Operation(
            summary = "내 Scout 프로필 및 활동 자격 조회",
            description = "Scout 프로필을 아직 신청하지 않은 사용자는 404 SCOUT_PROFILE_NOT_FOUND를 받습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scout 프로필 및 활동 자격 조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scout 프로필을 찾을 수 없음 (SCOUT_PROFILE_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "SCOUT_PROFILE_NOT_FOUND",
                                    value = """
                                            {"message":"Scout 프로필을 찾을 수 없습니다.","code":"SCOUT_PROFILE_NOT_FOUND"}
                                            """
                            )
                    )
            )
    })
    public ScoutProfileResponse get(
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.get(user.userId());
    }

    @PutMapping
    @Operation(
            summary = "내 Scout 프로필 수정",
            description = """
                    활성 일반 사용자이며 프로필 상태가 PENDING 또는 ACTIVE일 때만 수정할 수 있습니다.
                    SUSPENDED 또는 REVOKED 프로필은 409 INVALID_SCOUT_PROFILE_STATE를 반환합니다.
                    활동 자격 상태(PENDING, ELIGIBLE, SUSPENDED, EXPIRED, REVOKED)는 프로필 수정 가능 여부를
                    변경하지 않으며 Scout 활동 가능 여부를 판단하는 데 사용합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scout 프로필 수정 성공"),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아님 (SCOUT_PROFILE_ACCOUNT_REQUIRED)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "SCOUT_PROFILE_ACCOUNT_REQUIRED",
                                    value = """
                                            {"message":"활성 일반 사용자만 Scout 프로필을 신청할 수 있습니다.","code":"SCOUT_PROFILE_ACCOUNT_REQUIRED"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scout 프로필을 찾을 수 없음 (SCOUT_PROFILE_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "SCOUT_PROFILE_NOT_FOUND",
                                    value = """
                                            {"message":"Scout 프로필을 찾을 수 없습니다.","code":"SCOUT_PROFILE_NOT_FOUND"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "현재 프로필 상태에서 수정 불가 (INVALID_SCOUT_PROFILE_STATE)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "INVALID_SCOUT_PROFILE_STATE",
                                    value = """
                                            {"message":"현재 Scout 프로필 상태에서는 요청을 처리할 수 없습니다.","code":"INVALID_SCOUT_PROFILE_STATE"}
                                            """
                            )
                    )
            )
    })
    public ScoutProfileResponse update(
            @Valid @RequestBody ScoutProfileRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.update(user.userId(), request);
    }
}
