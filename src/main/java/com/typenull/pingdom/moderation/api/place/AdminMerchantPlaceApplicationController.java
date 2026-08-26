package com.typenull.pingdom.moderation.api.place;

import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Web 통합 신청의 관리자 심사 API입니다. */
@RestController
@RequiredArgsConstructor
@AdminOnly
@RequestMapping("/admin/merchant-place-applications")
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminMerchantPlaceApplicationController {
    private final MerchantPlaceApplicationService service;

    @GetMapping
    @Operation(summary = "관리자 Merchant 장소 신청 목록 조회")
    public AdminMerchantPlaceApplicationPageResponse list(
            @RequestParam(required = false) PlaceRegistrationStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.listForAdmin(admin.userId(), status, page, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "관리자 Merchant 장소 신청 상세 조회")
    public AdminMerchantPlaceApplicationResponse get(
            @PathVariable Long id,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.getForAdmin(admin.userId(), id);
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "관리자 Merchant 장소 신청 첨부파일 목록 조회")
    public List<AdminMerchantPlaceApplicationAttachmentResponse> listAttachments(
            @PathVariable Long id,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.listAttachmentsForAdmin(admin.userId(), id);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/content")
    @Operation(summary = "관리자 Merchant 장소 신청 첨부파일 다운로드")
    public ResponseEntity<byte[]> attachmentContent(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        MerchantPlaceApplicationService.DownloadedAttachment attachment =
                service.downloadAttachmentForAdmin(admin.userId(), id, attachmentId);
        return ResponseEntity.ok()
                .contentType(resolveContentType(attachment.contentType()))
                .body(attachment.bytes());
    }

    private MediaType resolveContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "관리자 Merchant 장소 신청 승인")
    public MerchantPlaceApplicationResponse approve(
            @PathVariable Long id,
            @Valid @RequestBody MerchantPlaceApplicationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.approve(admin.userId(), id, request);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "관리자 Merchant 장소 신청 반려")
    public MerchantPlaceApplicationResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody MerchantPlaceApplicationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.reject(admin.userId(), id, request);
    }
}
