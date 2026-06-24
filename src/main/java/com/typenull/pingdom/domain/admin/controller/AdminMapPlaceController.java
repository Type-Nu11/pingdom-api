package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.domain.admin.dto.place.AdminPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.domain.admin.dto.place.AdminPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.domain.admin.service.AdminMapPlaceQueryService;
import com.typenull.pingdom.domain.admin.service.AdminMapPlaceService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMapPlaceController {

    private final AdminMapPlaceQueryService adminMapPlaceQueryService;
    private final AdminMapPlaceService adminMapPlaceService;

    @GetMapping
    @Operation(
            summary = "관리자 장소 목록 조회",
            description = "관리자가 등록된 장소 목록을 페이지 단위로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "places": [
                                                {
                                                  "id": 1,
                                                  "name": "진주성",
                                                  "address": "경상남도 진주시 남강로 626",
                                                  "latitude": 35.1894,
                                                  "longitude": 128.0789,
                                                  "userId": 3
                                                }
                                              ],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 1,
                                              "totalPages": 1,
                                              "hasNext": false
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminMapPlaceResponse listPlaces(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminMapPlaceQueryService.listPlaces(page, limit);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "관리자 장소 상세 조회",
            description = "관리자가 특정 장소의 기본 정보와 연결된 게시글 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "name": "진주성",
                                              "address": "경상남도 진주시 남강로 626",
                                              "latitude": 35.1894,
                                              "longitude": 128.0789,
                                              "userId": 3,
                                              "postCount": 1,
                                              "posts": [
                                                {
                                                  "id": 10,
                                                  "imageUrl": "https://example.com/image.jpg",
                                                  "title": "야경 사진",
                                                  "description": "남강 야경입니다.",
                                                  "userId": 3,
                                                  "username": "pingdom_user",
                                                  "createdAt": "2026-05-28T12:00:00",
                                                  "likeCount": 5
                                                }
                                              ]
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
    public AdminMapPlaceDetailResponse getPlace(
            @Parameter(description = "조회할 장소 ID", example = "1") @PathVariable Long id
    ) {
        return adminMapPlaceQueryService.getPlace(id);
    }

    @PatchMapping("/{id}/kakao-place-id")
    @Operation(
            summary = "관리자 장소 Kakao place id 수정",
            description = "관리자가 핀 좌표 기반 장소를 Kakao place id와 연결하거나 잘못 연결된 값을 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Kakao place id 수정 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPlaceKakaoPlaceIdUpdateResponse.class),
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
                                                "kakaoPlaceId": "kakaoPlaceId는 필수입니다."
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
                                              "message": "이미 다른 장소에 연결된 Kakao place id입니다. 기존 장소 병합으로 처리해주세요.",
                                              "code": "KAKAO_PLACE_ID_ALREADY_CONNECTED"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminPlaceKakaoPlaceIdUpdateResponse> updateKakaoPlaceId(
            @Parameter(description = "Kakao place id를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminPlaceKakaoPlaceIdUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updateKakaoPlaceId(adminUserId, placeId, request.kakaoPlaceId()));
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
        adminMapPlaceService.deletePlace(id);
        if (adminUser != null) {
            log.info("Admin force deleted place. adminUserId={}, placeId={}", adminUser.userId(), id);
        } else {
            log.info("Admin force deleted place. adminUserId=unknown, placeId={}", id);
        }
        return ResponseEntity.noContent().build();
    }
}
