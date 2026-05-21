package com.typenull.pingdom.domain.firebase.controller;

import com.typenull.pingdom.domain.firebase.dto.FcmImageIdRequest;
import com.typenull.pingdom.domain.firebase.dto.FcmTokenRequest;
import com.typenull.pingdom.domain.firebase.service.FcmService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FCM/Notification", description = "푸시 알림 및 기기 토큰 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/firebase")
public class FcmController {

    private final FcmService fcmService;

    @Operation(summary = "FCM 토큰 업데이트", description = "사용자의 기기 토큰을 최신화합니다. 로그인 시 또는 토큰 갱신 시 호출합니다.")
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
            @RequestBody FcmTokenRequest request) {
        fcmService.updateFcmToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "장소 좋아요 알림 전송", description = "특정 이미지에 좋아요를 누르면 해당 장소 소유자에게 푸시 알림을 보냅니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "알림 전송 요청 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(examples = @ExampleObject(value = "{\"message\": \"유효하지 않은 토큰입니다.\", \"code\": \"INVALID_TOKEN\"}"))),
            @ApiResponse(responseCode = "404", description = "사용자 또는 이미지를 찾을 수 없음",
                    content = @Content(examples = {
                            @ExampleObject(name = "image-not-found", value = "{\"message\": \"이미지를 찾을 수 없습니다.\", \"code\": \"IMAGE_NOT_FOUND\"}"),
                            @ExampleObject(name = "user-not-found", value = "{\"message\": \"사용자를 찾을 수 없습니다.\", \"code\": \"USER_NOT_FOUND\"}")
                    }))
    })
    @PostMapping("/like")
    public ResponseEntity<Void> likePlace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "좋아요 알림을 보낼 대상 이미지 ID",
                    required = true,
                    content = @Content(schema = @Schema(implementation = FcmImageIdRequest.class))
            )
            @RequestBody FcmImageIdRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        fcmService.likePlace(request.imageId(), user.userId());
        return ResponseEntity.ok().build();
    }
}
