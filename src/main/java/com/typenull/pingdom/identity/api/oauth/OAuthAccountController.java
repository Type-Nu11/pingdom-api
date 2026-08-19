package com.typenull.pingdom.identity.api.oauth;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.oauth.OAuthAccountDisconnectRequest;
import com.typenull.pingdom.identity.api.dto.oauth.OAuthAccountLinkStartResponse;
import com.typenull.pingdom.identity.api.dto.oauth.OAuthAccountResponse;
import com.typenull.pingdom.identity.application.command.OAuthAccountCommandService;
import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.infrastructure.oauth.OAuth2LinkCookieService;
import com.typenull.pingdom.identity.infrastructure.oauth.OAuth2LinkTokenService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/oauth-accounts")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class OAuthAccountController {

    private static final String GOOGLE_AUTHORIZATION_URL = "/oauth2/authorization/google";

    private final OAuthAccountCommandService oAuthAccountCommandService;
    private final OAuth2LinkTokenService oAuth2LinkTokenService;
    private final OAuth2LinkCookieService oAuth2LinkCookieService;

    @PostMapping("/google/link")
    @Operation(
            summary = "Google 계정 연결 시작",
            description = "현재 인증된 사용자 계정에 Google OAuth 계정을 연결하기 위한 OAuth 인증 흐름을 시작합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "연결 시작 성공",
                    content = @Content(
                            schema = @Schema(implementation = OAuthAccountLinkStartResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "provider": "GOOGLE",
                                              "authorizationUrl": "/oauth2/authorization/google",
                                              "message": "Google 계정 연결을 시작합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰"
            )
    })
    public ResponseEntity<OAuthAccountLinkStartResponse> startGoogleLink(
            @CurrentUser JwtAuthenticatedUser authenticatedUser,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String linkToken = oAuth2LinkTokenService.generate(authenticatedUser.userId());
        response.addHeader(HttpHeaders.SET_COOKIE, oAuth2LinkCookieService.createLinkCookie(request, linkToken).toString());

        return ResponseEntity.ok(new OAuthAccountLinkStartResponse(
                AuthProvider.GOOGLE.name(),
                GOOGLE_AUTHORIZATION_URL,
                "Google 계정 연결을 시작합니다."
        ));
    }

    @DeleteMapping("/google")
    @Operation(
            summary = "Google 계정 연결 해제",
            description = "현재 인증된 사용자 계정에서 Google OAuth 계정 연결을 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "연결 해제 성공",
                    content = @Content(
                            schema = @Schema(implementation = OAuthAccountResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "provider": "GOOGLE",
                                              "linked": false,
                                              "message": "Google 계정 연결을 해제했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰 또는 현재 비밀번호 불일치"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "연결된 Google 계정을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "마지막 로그인 수단 보호 정책 위반"
            )
    })
    public ResponseEntity<OAuthAccountResponse> unlinkGoogle(
            @CurrentUser JwtAuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) OAuthAccountDisconnectRequest request
    ) {
        String currentPassword = request == null ? null : request.currentPassword();
        oAuthAccountCommandService.unlinkGoogleAccount(authenticatedUser.userId(), currentPassword);

        return ResponseEntity.ok(new OAuthAccountResponse(
                AuthProvider.GOOGLE.name(),
                false,
                "Google 계정 연결을 해제했습니다."
        ));
    }
}
