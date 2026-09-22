package com.typenull.pingdom.moderation.api.place;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.time.LocalDateTime;
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
import org.springframework.format.annotation.DateTimeFormat;

/** Web 통합 신청의 관리자 심사 API. */
@RestController
@RequiredArgsConstructor
@AdminOnly
@RequestMapping("/admin/merchant-place-applications")
@ApiAudience(ApiAudience.Group.ADMIN)
@Tag(name = SwaggerTagCatalog.APPLICATION_REVIEW)
public class AdminMerchantPlaceApplicationController {
    private final MerchantPlaceApplicationService service;

    @GetMapping
    @Operation(
            summary = "관리자 Merchant 장소 신청 목록 조회",
            description = "keyword는 장소명과 신청자 username을 부분 일치로 검색합니다. NEW_PLACE는 신청 장소명, EXISTING_PLACE_CLAIM은 대상 기존 장소명을 검색합니다. submittedFrom·submittedTo는 신청 제출 시각(Asia/Seoul 기준 LocalDateTime)을 포함 경계로 필터링하며, DRAFT는 제출 시각이 없어 기간 필터에서 제외됩니다. 한쪽 기간 경계만 전달할 수 있고 종료 시각이 시작 시각보다 빠르면 400을 반환합니다. 빈 keyword는 검색 조건을 적용하지 않습니다."
    )
    public AdminMerchantPlaceApplicationPageResponse list(
            @Parameter(
                    description = "반복 전달 가능한 신청 상태 필터",
                    style = ParameterStyle.FORM,
                    explode = Explode.TRUE,
                    array = @ArraySchema(schema = @Schema(implementation = PlaceRegistrationStatus.class))
            )
            @RequestParam(name = "status", required = false) List<PlaceRegistrationStatus> statuses,
            @RequestParam(required = false) MerchantPlaceApplicationType applicationType,
            @Parameter(description = "장소명 또는 신청자 username 부분 일치 검색어. 공백 또는 빈 값은 미적용", example = "서울")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "신청 제출 시각 시작 경계(포함)", example = "2026-09-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedFrom,
            @Parameter(description = "신청 제출 시각 종료 경계(포함)", example = "2026-09-30T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.listForAdmin(
                admin.userId(), statuses, applicationType, keyword, submittedFrom, submittedTo, page, limit
        );
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
    @Operation(summary = "관리자 Merchant 장소 신청 승인",
            description = "NEW_PLACE 승인 시 장소 생성, Merchant Owner 연결과 신청의 COMPLETED 전이가 하나의 트랜잭션으로 완료됩니다. 신청자의 추가 완료 요청은 필요하지 않습니다.")
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
