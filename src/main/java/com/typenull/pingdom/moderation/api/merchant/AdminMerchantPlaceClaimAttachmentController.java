package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
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
public class AdminMerchantPlaceClaimAttachmentController {
    private final MerchantPlaceClaimAttachmentService service;

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long claimId, @PathVariable Long attachmentId,
            @CurrentUser JwtAuthenticatedUser admin) {
        MerchantPlaceClaimAttachmentService.DownloadedAttachment attachment =
                service.downloadForAdmin(admin.userId(), claimId, attachmentId);
        MediaType mediaType = MediaType.parseMediaType(attachment.contentType());
        return ResponseEntity.ok().contentType(mediaType).body(attachment.bytes());
    }
}
