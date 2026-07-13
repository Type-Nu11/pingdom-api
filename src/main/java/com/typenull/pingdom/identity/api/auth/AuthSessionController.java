package com.typenull.pingdom.identity.api.auth;

import com.typenull.pingdom.identity.api.dto.token.RefreshTokenResponse;
import com.typenull.pingdom.identity.application.service.auth.AuthService;
import com.typenull.pingdom.identity.application.service.auth.TokenRefreshResult;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.security.refresh.RefreshTokenCookieService;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Common", description = "앱/웹 공통")
public class AuthSessionController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @PostMapping("/token/refresh")
    @Operation(
            summary = "토큰 재발급",
            description = "HttpOnly Cookie의 Refresh Token을 검증한 뒤 Access Token을 재발급하고, 회전된 Refresh Token은 Cookie로 갱신합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 재발급 성공",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "회전된 HttpOnly Refresh Token Cookie",
                            schema = @Schema(type = "string")
                    ),
                    content = @Content(schema = @Schema(implementation = RefreshTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 리프레시 토큰",
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
                    responseCode = "404",
                    description = "토큰에 해당하는 사용자를 찾을 수 없음",
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
    @RateLimited(RateLimitAction.TOKEN_REFRESH)
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        TokenRefreshResult tokenRefreshResult = authService.refreshToken(readRefreshToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.issue(tokenRefreshResult.refreshToken()).toString());
        return ResponseEntity.ok(new RefreshTokenResponse(tokenRefreshResult.accessToken()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "로그아웃",
            description = "HttpOnly Cookie의 Refresh Token을 검증한 뒤 현재 저장된 Refresh Token을 제거하고 Cookie를 만료 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "로그아웃 성공",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "만료된 HttpOnly Refresh Token Cookie",
                            schema = @Schema(type = "string")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 리프레시 토큰",
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
                    responseCode = "404",
                    description = "토큰에 해당하는 사용자를 찾을 수 없음",
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
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            authService.logout(readRefreshToken(request));
            return ResponseEntity.noContent().build();
        } finally {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.expire().toString());
        }
    }

    private String readRefreshToken(HttpServletRequest request) {
        return refreshTokenCookieService.read(request)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_TOKEN));
    }
}
