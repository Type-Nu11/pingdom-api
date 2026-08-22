package com.typenull.pingdom.moderation.api.place.information;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeReviewRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportPageResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportReviewRequest;
import com.typenull.pingdom.place.application.service.place.information.PlaceInformationReportService;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/place-information-reports")
@RequiredArgsConstructor
@Validated
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminPlaceInformationReportController {

    private final PlaceInformationReportService placeInformationReportService;

    @GetMapping
    @Operation(summary = "관리자 장소 정보 신고 목록 조회")
    public PlaceInformationReportPageResponse list(
            @RequestParam(required = false) PlaceInformationReportStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return placeInformationReportService.listForAdmin(status, page, limit);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "관리자 장소 정보 신고 상세 조회")
    public PlaceInformationReportResponse get(@PathVariable Long reportId) {
        return placeInformationReportService.getForAdmin(reportId);
    }

    @PostMapping("/{reportId}/review")
    @Operation(summary = "관리자 장소 정보 신고 검토")
    public PlaceInformationReportResponse reviewReport(
            @PathVariable Long reportId,
            @Valid @RequestBody PlaceInformationReportReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return placeInformationReportService.reviewReport(admin.userId(), reportId, request);
    }

    @PostMapping("/{reportId}/disputes/{disputeId}/review")
    @Operation(summary = "관리자 장소 정보 반박 검토")
    public PlaceInformationDisputeResponse reviewDispute(
            @PathVariable Long reportId,
            @PathVariable Long disputeId,
            @Valid @RequestBody PlaceInformationDisputeReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return placeInformationReportService.reviewDispute(admin.userId(), reportId, disputeId, request);
    }
}
