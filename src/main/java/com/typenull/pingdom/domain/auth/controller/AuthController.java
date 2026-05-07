package com.typenull.pingdom.domain.auth.controller;

import com.typenull.pingdom.domain.auth.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.signup.UserResponse;
import com.typenull.pingdom.domain.auth.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.domain.auth.dto.token.RefreshTokenResponse;
import com.typenull.pingdom.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(
            summary = "회원가입",
            description = "아이디, 이름, 이메일, 비밀번호를 입력받아 새 사용자를 생성합니다. 이메일이 포함되면 인증 메일 발송 절차가 함께 시작됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))
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
                                                "username": "아이디는 4자 이상 50자 이하여야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 아이디",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 사용 중인 아이디입니다.",
                                              "code": "DUPLICATE_USERNAME"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signup(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "아이디와 비밀번호를 검증한 뒤 사용자 정보와 Access Token, Refresh Token을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
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
                                                "password": "비밀번호는 8자 이상이어야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "아이디 또는 비밀번호 불일치",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "아이디 또는 비밀번호가 올바르지 않습니다.",
                                              "code": "INVALID_CREDENTIALS"
                                            }
                                            """
                            )
                    )
            )
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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

    @PostMapping("/token/refresh")
    // 리프레시 토큰 재발급 요청 처리 메서드
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }
}
