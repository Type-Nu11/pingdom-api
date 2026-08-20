package com.typenull.pingdom.moderation.api.place.duplicate;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeRestoreResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateCandidateListResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateCandidateResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateDecisionRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateMergeRequest;
import com.typenull.pingdom.moderation.application.query.place.management.AdminMapPlaceQueryService;
import com.typenull.pingdom.moderation.application.service.place.duplicate.AdminPlaceDuplicateService;
import com.typenull.pingdom.moderation.application.service.place.merge.AdminPlaceMergeService;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
/** 중복 장소 후보의 판정·병합·복구 관리 요청을 관리자 서비스로 전달합니다. */
public class AdminPlaceDuplicateController {

    private final AdminMapPlaceQueryService adminMapPlaceQueryService;
    private final AdminPlaceMergeService adminPlaceMergeService;
    private final AdminPlaceDuplicateService adminPlaceDuplicateService;

    @GetMapping("/duplicate-candidates")
    @Operation(summary = "관리자 중복 장소 후보 조회")
    public AdminPlaceDuplicateCandidateListResponse listDuplicateCandidates(
            @RequestParam(defaultValue = "PENDING") PlaceDuplicateDecisionStatus status
    ) {
        return adminPlaceDuplicateService.list(status);
    }

    @GetMapping("/duplicate-candidates/{candidateId}")
    @Operation(summary = "관리자 중복 장소 후보 상세 조회")
    public AdminPlaceDuplicateCandidateResponse getDuplicateCandidate(@PathVariable Long candidateId) {
        return adminPlaceDuplicateService.get(candidateId);
    }

    @PostMapping("/duplicate-candidates/{candidateId}/confirm")
    @Operation(summary = "관리자 중복 장소 후보 확정")
    public AdminPlaceDuplicateCandidateResponse confirmDuplicateCandidate(
            @PathVariable Long candidateId,
            @Valid @RequestBody AdminPlaceDuplicateDecisionRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminPlaceDuplicateService.confirm(adminUser.userId(), candidateId, request.reviewNote());
    }

    @PostMapping("/duplicate-candidates/{candidateId}/reject")
    @Operation(summary = "관리자 중복 장소 후보 거절")
    public AdminPlaceDuplicateCandidateResponse rejectDuplicateCandidate(
            @PathVariable Long candidateId,
            @Valid @RequestBody AdminPlaceDuplicateDecisionRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminPlaceDuplicateService.reject(adminUser.userId(), candidateId, request.reviewNote());
    }

    @PostMapping("/duplicate-candidates/{candidateId}/merge")
    @Operation(summary = "관리자 확정 중복 장소 병합")
    public AdminMapPlaceMergeResponse mergeDuplicateCandidate(
            @PathVariable Long candidateId,
            @Valid @RequestBody AdminPlaceDuplicateMergeRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminPlaceDuplicateService.merge(adminUser.userId(), candidateId, request.targetPlaceId());
    }

    @GetMapping("/duplicates")
    @Operation(
            summary = "관리자 중복 장소 목록 조회",
            description = "관리자가 병합 대상이 될 수 있는 중복 장소 그룹을 조회합니다."
    )
    public AdminMapPlaceDuplicateResponse listDuplicatePlaces() {
        return adminMapPlaceQueryService.listDuplicatePlaces();
    }

    @GetMapping("/duplicates/{id}")
    @Operation(
            summary = "관리자 중복 장소 상세 조회",
            description = "관리자가 특정 장소의 중복 후보 목록을 조회합니다."
    )
    public AdminMapPlaceDuplicateDetailResponse getDuplicatePlace(
            @Parameter(description = "중복 후보를 확인할 장소 ID", example = "10")
            @PathVariable("id") Long placeId
    ) {
        return adminMapPlaceQueryService.getDuplicatePlace(placeId);
    }

    @PostMapping("/merge")
    @Operation(
            summary = "관리자 중복 장소 병합",
            description = "관리자가 중복 장소의 참조 데이터를 대상 장소로 옮기고 원본 장소를 병합합니다."
    )
    public ResponseEntity<AdminMapPlaceMergeResponse> mergePlaces(
            @Valid @RequestBody AdminMapPlaceMergeRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        AdminMapPlaceMergeResponse response = adminPlaceMergeService.mergePlaces(adminUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/merge-histories")
    @Operation(
            summary = "관리자 장소 병합 이력 조회",
            description = "관리자가 최근 장소 병합 이력을 조회합니다."
    )
    public AdminPlaceMergeHistoryResponse listMergeHistories() {
        return adminPlaceMergeService.listMergeHistories();
    }

    @PostMapping("/merge-histories/{historyId}/restore")
    @Operation(
            summary = "관리자 장소 병합 복구",
            description = "관리자가 저장된 장소 병합 이력을 기준으로 복구합니다."
    )
    public ResponseEntity<AdminPlaceMergeRestoreResponse> restoreMerge(
            @PathVariable Long historyId,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceMergeService.restoreMerge(adminUserId, historyId));
    }
}
