package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentOrphanService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/merchant-place-claims/attachments")
@RequiredArgsConstructor
@AdminOnly
public class AdminMerchantPlaceClaimAttachmentCleanupController {
    private final MerchantPlaceClaimAttachmentOrphanService orphanService;

    @PostMapping("/orphan-cleanup")
    @Operation(summary = "관리자 Merchant 장소 Claim 고아 첨부파일 정리")
    public ResponseEntity<Map<String, Integer>> cleanup(
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(Map.of("requestedDeletionCount", orphanService.requestOrphanDeletion(limit)));
    }
}
