package com.typenull.pingdom.identity.api.auth;

import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.application.service.auth.AuthService;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Common", description = "앱/웹 공통")
public class PasswordResetController {

    private final AuthService authService;

    @PostMapping("/password-reset/request")
    @Operation(
            summary = "비밀번호 재설정 토큰 발급",
            description = "이메일 주소로 비밀번호 재설정 토큰을 발급하고 메일 발송 이벤트를 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "비밀번호 재설정 요청 접수"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "email": "이메일 형식이 올바르지 않습니다."
                                              }
                                            }
                                            """
                            )
                    )
            )
    })
    @RateLimited(RateLimitAction.PASSWORD_RESET_REQUEST)
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(
            summary = "비밀번호 재설정 완료",
            description = "메일로 발급된 재설정 토큰을 검증해 새 비밀번호를 설정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "비밀번호 재설정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 또는 유효하지 않은 재설정 토큰",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "invalid-token",
                                            value = """
                                                    {
                                                      "message": "비밀번호 재설정 토큰이 올바르지 않습니다.",
                                                      "code": "INVALID_PASSWORD_RESET_TOKEN"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "expired-token",
                                            value = """
                                                    {
                                                      "message": "비밀번호 재설정 토큰이 만료되었습니다.",
                                                      "code": "EXPIRED_PASSWORD_RESET_TOKEN"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @RateLimited(RateLimitAction.PASSWORD_RESET_CONFIRM)
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.noContent().build();
    }
}
