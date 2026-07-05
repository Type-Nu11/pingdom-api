package com.typenull.pingdom.identity.api;

import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.application.service.AuthService;
import com.typenull.pingdom.shared.ratelimit.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Common", description = "앱/웹 공통")
public class AuthLoginController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "아이디와 비밀번호를 검증한 뒤 사용자 정보와 Access Token, Refresh Token을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "username": "pingdom_user",
                                              "email": "pingdom@example.com",
                                              "birthYear": 1998,
                                              "profileImageUrl": "https://cdn.pingdom.com/profiles/user1.png",
                                              "language": "ko",
                                              "country": "KR",
                                              "message": "로그인에 성공했습니다.",
                                              "accessToken": "eyJhbGciOiJIUzI1NiJ9.access.token",
                                              "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh.token"
                                            }
                                            """
                            )
                    )
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
    @RateLimited(RateLimitAction.LOGIN)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/admin/login")
    @Operation(
            summary = "관리자 로그인",
            description = "관리자 계정만 관리자 페이지 전용 Access Token과 Refresh Token을 발급받습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 로그인 성공",
                    content = @Content(
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "username": "admin_user",
                                              "email": "admin@example.com",
                                              "birthYear": 1990,
                                              "profileImageUrl": null,
                                              "language": "ko",
                                              "country": "KR",
                                              "message": "로그인에 성공했습니다.",
                                              "accessToken": "eyJhbGciOiJIUzI1NiJ9.access.token",
                                              "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh.token"
                                            }
                                            """
                            )
                    )
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ADMIN_ACCESS_REQUIRED"
                                            }
                                            """
                            )
                    )
            )
    })
    @RateLimited(RateLimitAction.LOGIN)
    public LoginResponse adminLogin(@Valid @RequestBody LoginRequest request) {
        return authService.adminLogin(request);
    }
}
