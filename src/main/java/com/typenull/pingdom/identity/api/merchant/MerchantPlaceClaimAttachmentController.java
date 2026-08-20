package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimAttachmentResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAttachmentService;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@ActiveMerchantOwnerOnly
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceClaimAttachmentController {
    private final MerchantPlaceClaimAttachmentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Merchant 장소 Claim 첨부파일 업로드")
    public MerchantPlaceClaimAttachmentResponse upload(@PathVariable Long claimId,
            @RequestParam MerchantPlaceClaimAttachmentType documentType,
            @RequestParam MultipartFile file,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.upload(user.userId(), claimId, documentType, file);
    }

    @GetMapping
    @Operation(summary = "Merchant 장소 Claim 첨부파일 목록 조회")
    public List<MerchantPlaceClaimAttachmentResponse> list(@PathVariable Long claimId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.list(user.userId(), claimId);
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(summary = "Merchant 장소 Claim 첨부파일 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long claimId, @PathVariable Long attachmentId,
            @CurrentUser JwtAuthenticatedUser user) {
        service.delete(user.userId(), claimId, attachmentId);
    }

    @PostMapping("/reorder")
    @Operation(summary = "Merchant 장소 Claim 첨부파일 순서 변경")
    public void reorder(@PathVariable Long claimId, @RequestParam List<Long> attachmentIds,
            @CurrentUser JwtAuthenticatedUser user) {
        service.reorder(user.userId(), claimId, attachmentIds);
    }
}
