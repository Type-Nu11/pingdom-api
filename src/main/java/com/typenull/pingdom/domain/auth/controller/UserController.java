package com.typenull.pingdom.domain.auth.controller;

import com.typenull.pingdom.domain.auth.security.JwtAuthenticationFilter.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 현재 인증 사용자 탈퇴 처리 컨트롤러
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @DeleteMapping("/me")
    // 현재 로그인 사용자 탈퇴 처리 메서드
    public ResponseEntity<Void> withdraw(Authentication authentication) {
        JwtAuthenticatedUser authenticatedUser = (JwtAuthenticatedUser) authentication.getPrincipal();
        authService.withdraw(authenticatedUser.userId());
        return ResponseEntity.noContent().build();
    }
}
