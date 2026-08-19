package com.typenull.pingdom.identity.api.user;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.identity.application.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

// 현재 인증 사용자 탈퇴 처리 컨트롤러
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class UserController {

    private final AuthService authService;

    @DeleteMapping("/me")
    @Operation(
            summary = "회원탈퇴",
            description = "현재 인증된 사용자의 개인정보를 익명화하고 탈퇴 상태로 전환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "회원탈퇴 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
            )
    })
    public ResponseEntity<Void> withdraw(
            @CurrentUser JwtAuthenticatedUser authenticatedUser
    ) {
        authService.withdraw(authenticatedUser.userId());
        return ResponseEntity.noContent().build();
    }
}
