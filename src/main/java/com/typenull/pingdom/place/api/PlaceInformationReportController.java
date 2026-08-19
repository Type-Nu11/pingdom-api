package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportPageResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportResponse;
import com.typenull.pingdom.place.application.service.place.information.PlaceInformationReportService;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App Place", description = "앱용 장소 API")
public class PlaceInformationReportController {

    private final PlaceInformationReportService placeInformationReportService;

    @PostMapping("/places/{placeId}/information-reports")
    @Operation(summary = "장소 정보 신고 생성")
    public ResponseEntity<PlaceInformationReportResponse> submit(
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceInformationReportCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser principal = requireUser(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(placeInformationReportService.submit(principal.userId(), placeId, request));
    }

    @GetMapping("/places/information-reports")
    @Operation(summary = "내 장소 정보 신고 목록 조회")
    public PlaceInformationReportPageResponse listMine(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser principal = requireUser(user);
        return placeInformationReportService.listMine(principal.userId(), page, limit);
    }

    @GetMapping("/places/information-reports/{reportId}")
    @Operation(summary = "내 장소 정보 신고 상세 조회")
    public PlaceInformationReportResponse getMine(
            @PathVariable Long reportId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser principal = requireUser(user);
        return placeInformationReportService.getMine(principal.userId(), reportId);
    }

    @PostMapping("/places/information-reports/{reportId}/disputes")
    @Operation(summary = "장소 정보 신고 반박 생성")
    public ResponseEntity<PlaceInformationDisputeResponse> submitDispute(
            @PathVariable Long reportId,
            @Valid @RequestBody PlaceInformationDisputeCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser principal = requireUser(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(placeInformationReportService.submitDispute(principal.userId(), reportId, request));
    }

    private JwtAuthenticatedUser requireUser(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_FORBIDDEN));
    }
}
