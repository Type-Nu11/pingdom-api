package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.service.AdminMapPlaceService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMapPlaceController {

    private final AdminMapPlaceService adminMapPlaceService;

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Void> forceDeletePlace(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        adminMapPlaceService.deletePlace(id);
        if (adminUser != null) {
            log.info("Admin force deleted place. adminUserId={}, placeId={}", adminUser.userId(), id);
        } else {
            log.info("Admin force deleted place. adminUserId=unknown, placeId={}", id);
        }
        return ResponseEntity.noContent().build();
    }
}
