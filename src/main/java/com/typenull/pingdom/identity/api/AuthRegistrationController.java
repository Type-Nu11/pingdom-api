package com.typenull.pingdom.identity.api;

import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Common", description = "앱/웹 공통")
public class AuthRegistrationController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(
            summary = "회원가입",
            description = "아이디, 이메일, 비밀번호와 기본 프로필 정보를 입력받아 새 사용자를 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "username": "pingdom_user",
                                              "email": "pingdom@example.com",
                                              "birthYear": 1998,
                                              "profileImageUrl": "https://cdn.pingdom.com/profiles/user1.png",
                                              "language": "ko",
                                              "country": "KR"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "invalid-username",
                                            value = """
                                                    {
                                                      "message": "입력값을 확인해주세요.",
                                                      "errors": {
                                                        "username": "아이디는 4자 이상 50자 이하여야 합니다."
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "invalid-email",
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
                                            name = "invalid-language",
                                            value = """
                                                    {
                                                      "message": "입력값을 확인해주세요.",
                                                      "errors": {
                                                        "language": "언어는 20자 이하여야 합니다."
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 아이디 또는 이메일",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "duplicate-username",
                                            value = """
                                                    {
                                                      "message": "이미 사용 중인 아이디입니다.",
                                                      "code": "DUPLICATE_USERNAME"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "duplicate-email",
                                            value = """
                                                    {
                                                      "message": "이미 사용 중인 이메일입니다.",
                                                      "code": "DUPLICATE_EMAIL"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @RateLimited(RateLimitAction.SIGNUP)
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signup(request));
    }
}
