package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.dto.ban.BanRequest;
import com.typenull.pingdom.domain.admin.dto.ban.BanResponse;
import com.typenull.pingdom.domain.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/ban/{userId}")
    public BanResponse banUser(@PathVariable Long userId, @RequestBody(required = false) BanRequest request) {
        String reason = request == null ? null : request.reason();
        return adminUserService.banUser(userId, reason);
    }
}
