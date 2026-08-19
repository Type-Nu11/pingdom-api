package com.typenull.pingdom.moderation.api.place.quality;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.discovery.AdminMapPlaceDiscoveryStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.discovery.AdminMapPlaceDiscoveryStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceCreateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceReviewRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateResponse;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminPlaceQualityService;
import com.typenull.pingdom.moderation.application.service.place.operating.AdminPlaceOperatingScheduleService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@Slf4j
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
/** 관리자 장소의 좌표·식별자·운영·관광·검증 품질을 수정하는 API 진입점입니다. */
public class AdminPlaceQualityController {

    private final AdminMapPlaceService adminMapPlaceService;
    private final AdminPlaceQualityService adminPlaceQualityService;
    private final AdminPlaceOperatingScheduleService adminPlaceOperatingScheduleService;

    @PatchMapping("/{id}/coordinates")
    @Operation(
            summary = "관리자 장소 좌표 수정",
            description = "관리자가 잘못 등록된 장소의 위도와 경도를 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 좌표 수정 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceCoordinateUpdateResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "placeId": 1,
                                              "latitude": 35.1796,
                                              "longitude": 128.1076,
                                              "message": "장소 좌표를 수정했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "latitude": "위도는 90.0 이하여야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminMapPlaceCoordinateUpdateResponse> updatePlaceCoordinates(
            @Parameter(description = "좌표를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceCoordinateUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.updatePlaceCoordinates(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/geocoding")
    @Operation(
            summary = "관리자 장소 주소·좌표 보정",
            description = "관리자가 대표 주소, 정규화 주소와 좌표를 함께 보정하며 출처는 ADMIN으로 기록합니다."
    )
    public ResponseEntity<AdminMapPlaceGeocodingUpdateResponse> updatePlaceGeocoding(
            @Parameter(description = "주소와 좌표를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceGeocodingUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.updatePlaceGeocoding(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/kakao-place-id")
    @Operation(
            summary = "관리자 장소 Kakao place id 수정",
            description = "관리자가 장소에 연결된 Kakao place id를 재연결하거나 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Kakao place id 수정 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceKakaoPlaceIdUpdateResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "placeId": 1,
                                              "kakaoPlaceId": "27414316",
                                              "message": "장소 Kakao place id를 수정했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "kakaoPlaceId": "카카오 장소 ID는 50자 이하여야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 다른 장소에 연결된 Kakao place id",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 다른 장소에 연결된 Kakao place id입니다.",
                                              "code": "PLACE_KAKAO_PLACE_ID_CONFLICT"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminMapPlaceKakaoPlaceIdUpdateResponse> updatePlaceKakaoPlaceId(
            @Parameter(description = "Kakao place id를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceKakaoPlaceIdUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.updatePlaceKakaoPlaceId(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/tourist-info")
    @Operation(
            summary = "관리자 장소 관광 정보 수정",
            description = "관리자가 장소의 영문명, 관광객용 요약, 관광 카테고리를 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 관광 정보 수정 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceTouristInfoUpdateResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "placeId": 1,
                                              "englishName": "Jinju Castle",
                                              "touristSummary": "A historic fortress overlooking the Nam River.",
                                              "touristCategories": ["K_POP", "CAFE", "POP_UP"],
                                              "message": "장소 관광 정보를 수정했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "reason": "수정 사유는 필수입니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminMapPlaceTouristInfoUpdateResponse> updatePlaceTouristInfo(
            @Parameter(description = "관광 정보를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceTouristInfoUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.updatePlaceTouristInfo(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/operating-status")
    @Operation(
            summary = "관리자 장소 운영 상태 확인",
            description = "관리자가 확인한 장소 운영 상태를 기록하고 최신 확인 시각을 서버 시간으로 갱신합니다. 비운영 장소는 앱 장소 조회와 추천에서 숨겨집니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 운영 상태 수정 성공",
                    content = @Content(schema = @Schema(implementation = AdminMapPlaceOperatingStatusUpdateResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    public ResponseEntity<AdminMapPlaceOperatingStatusUpdateResponse> updatePlaceOperatingStatus(
            @Parameter(description = "운영 상태를 확인할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceOperatingStatusUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.updatePlaceOperatingStatus(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/discovery-status")
    @Operation(
            summary = "관리자 장소 탐색 노출 상태 수정",
            description = "관리자가 장소를 공개 탐색, 자동완성, 북마크 목록, 추천 후보에 노출할지 제어합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 탐색 노출 상태 수정 성공",
                    content = @Content(schema = @Schema(implementation = AdminMapPlaceDiscoveryStatusUpdateResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    public ResponseEntity<AdminMapPlaceDiscoveryStatusUpdateResponse> updatePlaceDiscoveryStatus(
            @Parameter(description = "탐색 노출 상태를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceDiscoveryStatusUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.updatePlaceDiscoveryStatus(adminUserId, placeId, request));
    }

    @GetMapping("/{id}/information-evidence")
    @Operation(
            summary = "관리자 장소 정보 증빙 목록 조회",
            description = "관리자가 장소 정보 출처와 증빙 메타데이터를 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 정보 증빙 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminPlaceInformationEvidenceResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    public ResponseEntity<AdminPlaceInformationEvidenceResponse> getPlaceInformationEvidence(
            @Parameter(description = "증빙을 조회할 장소 ID", example = "1") @PathVariable("id") Long placeId
    ) {
        return ResponseEntity.ok(adminPlaceQualityService.getPlaceInformationEvidence(placeId));
    }

    @PostMapping("/{id}/information-evidence")
    @Operation(
            summary = "관리자 장소 정보 증빙 등록",
            description = "관리자가 장소 정보 출처와 증빙 메타데이터를 등록합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 정보 증빙 등록 성공",
                    content = @Content(schema = @Schema(implementation = AdminPlaceInformationEvidenceUpdateResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    public ResponseEntity<AdminPlaceInformationEvidenceUpdateResponse> createPlaceInformationEvidence(
            @Parameter(description = "증빙을 등록할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminPlaceInformationEvidenceCreateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.createPlaceInformationEvidence(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/information-evidence/{evidenceId}/review")
    @Operation(
            summary = "관리자 장소 정보 증빙 검토",
            description = "관리자가 장소 정보 증빙을 승인 또는 반려하고 장소 정보 검증 요약 상태를 갱신합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 정보 증빙 검토 성공",
                    content = @Content(schema = @Schema(implementation = AdminPlaceInformationEvidenceUpdateResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "장소 또는 증빙을 찾을 수 없음")
    })
    public ResponseEntity<AdminPlaceInformationEvidenceUpdateResponse> reviewPlaceInformationEvidence(
            @Parameter(description = "증빙을 검토할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Parameter(description = "증빙 ID", example = "10") @PathVariable Long evidenceId,
            @Valid @RequestBody AdminPlaceInformationEvidenceReviewRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceQualityService.reviewPlaceInformationEvidence(adminUserId, placeId, evidenceId, request));
    }

    @PatchMapping("/{id}/operating-schedule")
    @Operation(
            summary = "관리자 장소 영업시간 일정 수정",
            description = "관리자가 요일별 정규 영업시간과 특정 날짜 휴무·대체 영업시간을 전체 교체합니다. 수동 운영 상태는 변경하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 영업시간 일정 수정 성공",
                    content = @Content(schema = @Schema(implementation = AdminMapPlaceOperatingScheduleUpdateResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AdminMapPlaceOperatingScheduleUpdateResponse> updatePlaceOperatingSchedule(
            @Parameter(description = "영업시간 일정을 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceOperatingScheduleUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceOperatingScheduleService.updatePlaceOperatingSchedule(adminUserId, placeId, request));
    }

    @DeleteMapping("/{id}/delete")
    @Operation(
            summary = "관리자 장소 삭제",
            description = "관리자가 장소를 강제로 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "장소 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "연결된 기간형 이벤트가 있어 장소를 삭제할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> forceDeletePlace(
            @Parameter(description = "강제 삭제할 장소 ID", example = "5") @PathVariable Long id,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        adminMapPlaceService.deletePlace(id, adminUserId);
        if (adminUser != null) {
            log.info("Admin force deleted place. adminUserId={}, placeId={}", adminUser.userId(), id);
        } else {
            log.info("Admin force deleted place. adminUserId=unknown, placeId={}", id);
        }
        return ResponseEntity.noContent().build();
    }
}
