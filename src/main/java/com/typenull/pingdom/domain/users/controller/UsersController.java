package com.typenull.pingdom.domain.users.controller;

import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.users.dto.ChangePasswordRequest;
import com.typenull.pingdom.domain.users.dto.ChangeUsernameRequest;
import com.typenull.pingdom.domain.users.dto.MyPageResponse;
import com.typenull.pingdom.domain.users.service.ChangeInfoService;
import com.typenull.pingdom.domain.users.service.MyPageService;
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
    public ResponseEntity<MyPageResponse> getMyPageInfo(@AuthenticationPrincipal JwtAuthenticatedUser user) {
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
