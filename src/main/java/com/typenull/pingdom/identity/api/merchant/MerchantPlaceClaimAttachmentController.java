package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimAttachmentResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentService;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/merchant-owner/place-claims/{claimId}/attachments")
@RequiredArgsConstructor
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
public class MerchantPlaceClaimAttachmentController {
    private final MerchantPlaceClaimAttachmentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantPlaceClaimAttachmentResponse upload(@PathVariable Long claimId,
            @RequestParam MerchantPlaceClaimAttachmentType documentType,
            @RequestParam MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.upload(user.userId(), claimId, documentType, file);
    }

    @GetMapping
    public List<MerchantPlaceClaimAttachmentResponse> list(@PathVariable Long claimId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.list(user.userId(), claimId);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long claimId, @PathVariable Long attachmentId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        service.delete(user.userId(), claimId, attachmentId);
    }
}
