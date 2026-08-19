package com.typenull.pingdom.notification.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.notification.api.dto.settings.NotificationSettingResponse;
import com.typenull.pingdom.notification.api.dto.settings.NotificationSettingUpdateRequest;

import com.typenull.pingdom.notification.application.service.NotificationSettingService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App", description = "앱 전용 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications/settings")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @GetMapping
    @Operation(summary = "알림 설정 조회", description = "현재 사용자의 알림 타입별 수신 여부와 quiet hours 설정을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = NotificationSettingResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "newHotplaceEnabled": true,
                                              "newLikeEnabled": true,
                                              "quietHoursEnabled": false,
                                              "quietHoursStart": null,
                                              "quietHoursEnd": null,
                                              "timezone": "Asia/Seoul"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<NotificationSettingResponse> getSetting(
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(notificationSettingService.getSetting(user.userId()));
    }

    @PatchMapping
    @Operation(summary = "알림 설정 수정", description = "현재 사용자의 알림 타입별 수신 여부와 quiet hours 설정을 부분 수정합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = NotificationSettingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "timezone 또는 quiet hours 설정 오류",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "quiet hours 설정을 확인해주세요.",
                                              "code": "INVALID_QUIET_HOURS"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<NotificationSettingResponse> updateSetting(
            @CurrentUser JwtAuthenticatedUser user,
            @Valid @RequestBody NotificationSettingUpdateRequest request
    ) {
        return ResponseEntity.ok(notificationSettingService.updateSetting(user.userId(), request));
    }
}
