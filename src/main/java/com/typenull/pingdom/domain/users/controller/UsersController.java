package com.typenull.pingdom.domain.users.controller;

import com.typenull.pingdom.domain.users.dto.MyPageRequest;
import com.typenull.pingdom.domain.users.dto.MyPageResponse;
import com.typenull.pingdom.domain.users.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UsersController {

    private final MyPageService myPageService;

    public ResponseEntity<MyPageResponse> getMyPageInfo(@RequestBody MyPageRequest myPageRequest){
        return ResponseEntity.ok(myPageService.getMyPageInfo(myPageRequest));
    }
}
