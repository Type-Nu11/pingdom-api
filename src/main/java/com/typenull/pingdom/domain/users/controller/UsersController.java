package com.typenull.pingdom.domain.users.controller;

import com.typenull.pingdom.domain.users.dto.MyPageResponse;
import com.typenull.pingdom.domain.users.exception.MyPageErrorCode;
import com.typenull.pingdom.domain.users.exception.MyPageException;
import com.typenull.pingdom.domain.users.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final MyPageService myPageService;

    @GetMapping("/me")
    public ResponseEntity<MyPageResponse> getMyPageInfo(
            @RequestHeader("Authorization") String authorization
    ) {
        if (!authorization.startsWith("Bearer ")) {
            throw new MyPageException(MyPageErrorCode.INVALID_TOKEN);
        }

        String token = authorization.substring(7);

        return ResponseEntity.ok(myPageService.getMyPageInfo(token));
    }
}
