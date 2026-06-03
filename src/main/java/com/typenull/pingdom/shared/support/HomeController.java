package com.typenull.pingdom.shared.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Common", description = "앱/웹 공통")
public class HomeController {

    @GetMapping("/")
    @Operation(
            summary = "서버 상태 확인",
            description = "백엔드 서버 구동 여부와 주요 공개 엔드포인트 정보를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "서버 상태 조회 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "Pingdom Backend is running.",
                                              "availableEndpoints": ["/auth/signup", "/auth/login", "/auth/email/verify", "/auth/token/refresh", "/users/me"],
                                              "signupFields": ["username", "email", "password", "birthYear", "profileImageUrl", "language", "country"],
                                              "loginFields": ["username", "password"]
                                            }
                                            """
                            )
                    )
            )
    })
    public Map<String, Object> home() {
        return Map.of(
                "message", "Pingdom Backend is running.",
                "availableEndpoints", new String[]{"/auth/signup", "/auth/login", "/auth/email/verify", "/auth/token/refresh", "/users/me"},
                "signupFields", new String[]{"username", "email", "password", "birthYear", "profileImageUrl", "language", "country"},
                "loginFields", new String[]{"username", "password"}
        );
    }
}
