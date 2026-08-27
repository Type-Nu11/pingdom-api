package com.typenull.pingdom.place.api.registration;

import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationAttachmentService;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** 통합 Merchant 장소 신청 초안의 실제 파일 업로드 API입니다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/merchant-place-applications/{applicationId}/attachments")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceApplicationAttachmentController {

    private final MerchantPlaceApplicationAttachmentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Merchant 장소 신청 첨부파일 업로드",
            description = "NEW_PLACE와 EXISTING_PLACE_CLAIM 초안에만 업로드할 수 있습니다. 서버가 파일 형식·서명·크기·악성코드를 검증하고 private 저장소에 보관합니다.")
    public MerchantPlaceApplicationAttachmentResponse upload(
            @PathVariable Long applicationId,
            @RequestParam PlaceRegistrationAttachmentType documentType,
            @RequestParam MultipartFile file,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.upload(user.userId(), applicationId, documentType, file);
    }

    @GetMapping
    @Operation(summary = "Merchant 장소 신청 첨부파일 목록 조회")
    public List<MerchantPlaceApplicationAttachmentResponse> list(
            @PathVariable Long applicationId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.list(user.userId(), applicationId);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Merchant 장소 신청 첨부파일 삭제")
    public void delete(
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        service.delete(user.userId(), applicationId, attachmentId);
    }

    @PostMapping("/reorder")
    @Operation(summary = "Merchant 장소 신청 대표 이미지 순서 변경")
    public void reorder(
            @PathVariable Long applicationId,
            @RequestParam List<Long> attachmentIds,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        service.reorder(user.userId(), applicationId, attachmentIds);
    }
}
