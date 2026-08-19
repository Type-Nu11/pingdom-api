package com.typenull.pingdom.notification.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.notification.api.dto.fcm.FcmTokenRequest;

import com.typenull.pingdom.notification.application.service.FcmDeviceTokenService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App", description = "앱 전용 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/firebase")
public class FcmController {

    private final FcmDeviceTokenService fcmDeviceTokenService;
    @Operation(summary = "FCM 토큰 등록", description = "현재 기기의 FCM 토큰을 사용자에게 등록하거나 재등록합니다.")
    @PostMapping("/fcm-tokens")
    public ResponseEntity<Void> registerFcmToken(
            @CurrentUser JwtAuthenticatedUser user,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        fcmDeviceTokenService.registerToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 토큰 삭제", description = "로그아웃 또는 토큰 폐기 시 현재 기기의 FCM 토큰을 삭제합니다.")
    @DeleteMapping("/fcm-tokens")
    public ResponseEntity<Void> deleteFcmToken(
            @CurrentUser JwtAuthenticatedUser user,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        fcmDeviceTokenService.deleteToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }
}
