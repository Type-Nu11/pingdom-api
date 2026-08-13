package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentOrphanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/merchant-place-claims/attachments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantPlaceClaimAttachmentCleanupController {
    private final MerchantPlaceClaimAttachmentOrphanService orphanService;

    @PostMapping("/orphan-cleanup")
    public ResponseEntity<Map<String, Integer>> cleanup(
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(Map.of("requestedDeletionCount", orphanService.requestOrphanDeletion(limit)));
    }
}
