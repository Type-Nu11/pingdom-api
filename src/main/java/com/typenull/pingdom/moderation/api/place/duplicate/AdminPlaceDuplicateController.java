package com.typenull.pingdom.moderation.api.place.duplicate;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeRestoreResponse;
import com.typenull.pingdom.moderation.application.query.place.management.AdminMapPlaceQueryService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceDuplicateController {

    private final AdminMapPlaceQueryService adminMapPlaceQueryService;
    private final AdminMapPlaceService adminMapPlaceService;

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
            @RequestBody AdminMapPlaceMergeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        AdminMapPlaceMergeResponse response = adminMapPlaceService.mergePlaces(adminUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/merge-histories")
    @Operation(
            summary = "관리자 장소 병합 이력 조회",
            description = "관리자가 최근 장소 병합 이력을 조회합니다."
    )
    public AdminPlaceMergeHistoryResponse listMergeHistories() {
        return adminMapPlaceService.listMergeHistories();
    }

    @PostMapping("/merge-histories/{historyId}/restore")
    @Operation(
            summary = "관리자 장소 병합 복구",
            description = "관리자가 저장된 장소 병합 이력을 기준으로 복구합니다."
    )
    public ResponseEntity<AdminPlaceMergeRestoreResponse> restoreMerge(
            @PathVariable Long historyId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.restoreMerge(adminUserId, historyId));
    }
}
