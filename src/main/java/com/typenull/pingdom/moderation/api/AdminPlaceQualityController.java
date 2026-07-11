package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceCoordinateUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceCoordinateUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceGeocodingUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceGeocodingUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceTouristInfoUpdateResponse;
import com.typenull.pingdom.moderation.application.service.AdminMapPlaceService;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceQualityController {

    private final AdminMapPlaceService adminMapPlaceService;

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
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updatePlaceCoordinates(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/geocoding")
    @Operation(
            summary = "관리자 장소 주소·좌표 보정",
            description = "관리자가 대표 주소, 정규화 주소와 좌표를 함께 보정하며 출처는 ADMIN으로 기록합니다."
    )
    public ResponseEntity<AdminMapPlaceGeocodingUpdateResponse> updatePlaceGeocoding(
            @Parameter(description = "주소와 좌표를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceGeocodingUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updatePlaceGeocoding(adminUserId, placeId, request));
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
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updatePlaceKakaoPlaceId(adminUserId, placeId, request));
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
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updatePlaceTouristInfo(adminUserId, placeId, request));
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
            )
    })
    public ResponseEntity<Void> forceDeletePlace(
            @Parameter(description = "강제 삭제할 장소 ID", example = "5") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
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
