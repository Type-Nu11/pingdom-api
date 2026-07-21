package com.typenull.pingdom.moderation.api.place.operating;

import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingNoticeService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/place-operating-notices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceOperatingNoticeMaintenanceController {

    private final PlaceOperatingNoticeService placeOperatingNoticeService;

    @PostMapping("/expire")
    @Operation(summary = "관리자 상점 운영 상태 공지 만료 처리", description = "만료 시간이 지난 운영 상태 공지를 일괄 만료 처리합니다.")
    public ResponseEntity<Map<String, Integer>> expireDueNotices(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(Map.of("expiredCount", placeOperatingNoticeService.expireDueNotices(adminUserId)));
    }
}
