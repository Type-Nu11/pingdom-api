package com.typenull.pingdom.notification.api;

import com.typenull.pingdom.notification.api.dto.fcm.FcmTokenRequest;

import com.typenull.pingdom.notification.application.service.FcmDeviceTokenService;
import com.typenull.pingdom.shared.observability.LegacyApiEndpoint;
import com.typenull.pingdom.shared.observability.LegacyApiUsageMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FCM/Notification", description = "푸시 알림 및 기기 토큰 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/firebase")
public class FcmController {

    private final FcmDeviceTokenService fcmDeviceTokenService;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @Deprecated
    @Operation(
            summary = "FCM 토큰 업데이트",
            description = "하위 호환성을 위한 기존 엔드포인트입니다. 곧 지원 중단될 예정이므로 POST /firebase/fcm-tokens를 사용하세요."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 업데이트 성공"),
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
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(examples = @ExampleObject(value = "{\"message\": \"유효하지 않은 토큰입니다.\", \"code\": \"INVALID_TOKEN\"}")))
    })
    @PatchMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "최신 FCM 기기 토큰",
                    required = true,
                    content = @Content(schema = @Schema(implementation = FcmTokenRequest.class))
            )
            @Valid @RequestBody FcmTokenRequest request) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.FCM_TOKEN_UPDATE);
        fcmDeviceTokenService.registerToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 토큰 등록", description = "현재 기기의 FCM 토큰을 사용자에게 등록하거나 재등록합니다.")
    @PostMapping("/fcm-tokens")
    public ResponseEntity<Void> registerFcmToken(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        fcmDeviceTokenService.registerToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 토큰 삭제", description = "로그아웃 또는 토큰 폐기 시 현재 기기의 FCM 토큰을 삭제합니다.")
    @DeleteMapping("/fcm-tokens")
    public ResponseEntity<Void> deleteFcmToken(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        fcmDeviceTokenService.deleteToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }
}
