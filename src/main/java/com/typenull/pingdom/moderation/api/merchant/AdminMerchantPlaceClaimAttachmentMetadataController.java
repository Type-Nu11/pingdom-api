package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantPlaceClaimAttachmentResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchant-place-claims/{claimId}/attachments")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMerchantPlaceClaimAttachmentMetadataController {
    private final MerchantPlaceClaimAttachmentService service;

    @GetMapping
    public List<AdminMerchantPlaceClaimAttachmentResponse> list(@PathVariable Long claimId) {
        return service.listForAdmin(claimId);
    }
}
