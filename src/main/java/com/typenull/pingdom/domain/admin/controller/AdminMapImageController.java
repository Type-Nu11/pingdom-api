package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/map-images")
@RequiredArgsConstructor
@Slf4j
public class AdminMapImageController {

    private final AdminPictureService adminPictureService;

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> forceDeleteMapImage(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        adminPictureService.deletePicture(id);
        if (adminUser != null) {
            log.info("Admin force deleted map image. adminUserId={}, imageId={}", adminUser.userId(), id);
        } else {
            log.info("Admin force deleted map image. adminUserId=unknown, imageId={}", id);
        }
        return ResponseEntity.noContent().build();
    }
}

