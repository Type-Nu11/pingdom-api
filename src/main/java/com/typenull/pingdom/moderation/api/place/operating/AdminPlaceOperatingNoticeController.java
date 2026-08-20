package com.typenull.pingdom.moderation.api.place.operating;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCancelRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeUpdateRequest;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingNoticeService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places/{placeId}/operating-notices")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminPlaceOperatingNoticeController {

    private final PlaceOperatingNoticeService placeOperatingNoticeService;

    @PostMapping
    @Operation(summary = "관리자 상점 운영 상태 공지 생성", description = "관리자가 상점 운영 상태 공지를 생성합니다.")
    public ResponseEntity<PlaceOperatingNoticeResponse> createNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Valid @RequestBody PlaceOperatingNoticeCreateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(placeOperatingNoticeService.createByAdmin(userId(adminUser), placeId, request));
    }

    @PatchMapping("/{noticeId}")
    @Operation(summary = "관리자 상점 운영 상태 공지 수정", description = "관리자가 상점 운영 상태 공지 내용을 수정합니다.")
    public ResponseEntity<PlaceOperatingNoticeResponse> updateNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Parameter(description = "공지 ID", example = "10") @PathVariable Long noticeId,
            @Valid @RequestBody PlaceOperatingNoticeUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.updateByAdmin(userId(adminUser), placeId, noticeId, request));
    }

    @PostMapping("/{noticeId}/cancel")
    @Operation(summary = "관리자 상점 운영 상태 공지 취소", description = "관리자가 예약 또는 활성 운영 상태 공지를 취소합니다.")
    public ResponseEntity<PlaceOperatingNoticeResponse> cancelNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Parameter(description = "공지 ID", example = "10") @PathVariable Long noticeId,
            @Valid @RequestBody PlaceOperatingNoticeCancelRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.cancelByAdmin(userId(adminUser), placeId, noticeId, request));
    }

    private Long userId(JwtAuthenticatedUser user) {
        return user == null ? null : user.userId();
    }
}
