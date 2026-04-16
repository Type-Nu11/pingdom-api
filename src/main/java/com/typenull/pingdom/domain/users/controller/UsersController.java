package com.typenull.pingdom.domain.users.controller;

import com.typenull.pingdom.domain.users.dto.ChangePasswordRequest;
import com.typenull.pingdom.domain.users.dto.ChangeUsernameRequest;
import com.typenull.pingdom.domain.users.dto.MyPageResponse;
import com.typenull.pingdom.domain.users.exception.MyPageErrorCode;
import com.typenull.pingdom.domain.users.exception.MyPageException;
import com.typenull.pingdom.domain.users.service.ChangeInfoService;
import com.typenull.pingdom.domain.users.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final MyPageService myPageService;
    private final ChangeInfoService changeInfoService;

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MyPageException(MyPageErrorCode.INVALID_TOKEN);
        }
        return authorization.substring(7);
    }

    @GetMapping("/me")
    public ResponseEntity<MyPageResponse> getMyPageInfo(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = extractToken(authorization);

        return ResponseEntity.ok(myPageService.getMyPageInfo(token));
    }

    @PostMapping("/change-pw")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String authorization){

        String token = extractToken(authorization);
        changeInfoService.changePassword(request,token);

        return ResponseEntity.ok("비밀번호 변경 완료");
    }

    @PostMapping("/change-id")
    public ResponseEntity<String> changeUsername(
            @RequestBody ChangeUsernameRequest request,
            @RequestHeader("Authorization") String authorization){

        String token = extractToken(authorization);
        changeInfoService.changeUsername(request,token);

        return ResponseEntity.ok("이름 변경 완료");
    }
}
