package com.typenull.pingdom.domain.users.controller;

import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.users.dto.ChangePasswordRequest;
import com.typenull.pingdom.domain.users.dto.ChangeUsernameRequest;
import com.typenull.pingdom.domain.users.dto.MyPageResponse;
import com.typenull.pingdom.domain.users.service.ChangeInfoService;
import com.typenull.pingdom.domain.users.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final MyPageService myPageService;
    private final ChangeInfoService changeInfoService;

    @GetMapping("/me")
    @Operation(
            summary = "내 정보 조회",
            description = "현재 인증된 사용자의 마이페이지 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MyPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청 또는 유효하지 않은 토큰",
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
            )
    })
    public ResponseEntity<MyPageResponse> getMyPageInfo(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        return ResponseEntity.ok(myPageService.getMyPageInfo(userId));
    }

    @PostMapping("/change-pw")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user){

        Long userId = user.userId();
        changeInfoService.changePassword(request,userId);

        return ResponseEntity.ok("비밀번호 변경 완료");
    }

    @PostMapping("/change-id")
    public ResponseEntity<String> changeUsername(
            @Valid @RequestBody ChangeUsernameRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user){

        Long userId = user.userId();
        changeInfoService.changeUsername(request,userId);

        return ResponseEntity.ok("이름 변경 완료");
    }
}
