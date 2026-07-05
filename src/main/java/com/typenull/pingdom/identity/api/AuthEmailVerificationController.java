package com.typenull.pingdom.identity.api;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.application.service.AuthService;
import com.typenull.pingdom.shared.ratelimit.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.RateLimited;
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
public class AuthEmailVerificationController {

    private final AuthService authService;

    @PostMapping("/email/resend")
    @Operation(
            summary = "인증 메일 재발송",
            description = "미인증 사용자의 이메일 인증 코드를 새로 발급하고 인증 메일을 다시 발송합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "인증 메일 재발송 성공"
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "이메일에 해당하는 사용자를 찾을 수 없음",
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
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 인증된 이메일",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 이메일 인증이 완료된 사용자입니다.",
                                              "code": "EMAIL_ALREADY_VERIFIED"
                                            }
                                            """
                            )
                    )
            )
    })
    @RateLimited(RateLimitAction.EMAIL_RESEND)
    public ResponseEntity<Void> resendVerificationEmail(
            @Valid @RequestBody EmailResendRequest request
    ) {
        authService.resendVerificationEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/verify")
    @Operation(
            summary = "이메일 인증",
            description = "이메일과 인증 코드를 검증해 사용자의 이메일 인증 상태를 완료 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이메일 인증 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 또는 유효하지 않은 인증 코드",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "validation-failure",
                                            value = """
                                                    {
                                                      "message": "입력값을 확인해주세요.",
                                                      "errors": {
                                                        "email": "이메일 형식이 올바르지 않습니다."
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "invalid-code",
                                            value = """
                                                    {
                                                      "message": "이메일 인증 코드가 올바르지 않습니다.",
                                                      "code": "INVALID_EMAIL_VERIFICATION_CODE"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "expired-code",
                                            value = """
                                                    {
                                                      "message": "이메일 인증 코드가 만료되었습니다.",
                                                      "code": "EXPIRED_EMAIL_VERIFICATION_CODE"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "이메일에 해당하는 사용자를 찾을 수 없음",
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
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok().build();
    }
}
