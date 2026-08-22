package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchant-place-claims/{claimId}/attachments")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminMerchantPlaceClaimAttachmentController {
    private final MerchantPlaceClaimAttachmentService service;

    @GetMapping("/{attachmentId}/content")
    @Operation(summary = "관리자 Merchant 장소 Claim 첨부파일 다운로드")
    public ResponseEntity<byte[]> content(@PathVariable Long claimId, @PathVariable Long attachmentId,
            @CurrentUser JwtAuthenticatedUser admin) {
        MerchantPlaceClaimAttachmentService.DownloadedAttachment attachment =
                service.downloadForAdmin(admin.userId(), claimId, attachmentId);
        MediaType mediaType = MediaType.parseMediaType(attachment.contentType());
        return ResponseEntity.ok().contentType(mediaType).body(attachment.bytes());
    }
}
