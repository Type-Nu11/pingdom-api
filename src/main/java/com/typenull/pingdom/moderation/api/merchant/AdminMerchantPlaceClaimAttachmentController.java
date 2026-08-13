package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchant-place-claims/{claimId}/attachments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantPlaceClaimAttachmentController {
    private final MerchantPlaceClaimAttachmentService service;

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long claimId, @PathVariable Long attachmentId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin) {
        MerchantPlaceClaimAttachmentService.DownloadedAttachment attachment =
                service.downloadForAdmin(admin.userId(), claimId, attachmentId);
        MediaType mediaType = MediaType.parseMediaType(attachment.contentType());
        return ResponseEntity.ok().contentType(mediaType).body(attachment.bytes());
    }
}
