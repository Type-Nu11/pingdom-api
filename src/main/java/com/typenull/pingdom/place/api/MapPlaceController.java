package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateRequest;
import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceCreateResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceUploadRequest;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
@Tag(name = "App Place", description = "앱용 장소 API")
public class MapPlaceController {

    private final MapPlaceService mapPlaceService;

    @PostMapping("/coordinates")
    @Operation(summary = "장소 좌표 생성/확정", description = "등록 버튼 클릭 시 호출하여 좌표 토큰을 발급합니다. 카카오 장소 ID는 선택값입니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "좌표 토큰 발급 성공",
                    content = @Content(schema = @Schema(implementation = PlaceCoordinateCreateResponse.class))
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
                                                "baseLatitude": "위도는 -90.0 이상이어야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
            )
    })
    public ResponseEntity<PlaceCoordinateCreateResponse> createCoordinates(
            @Valid @RequestBody PlaceCoordinateCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        PlaceCoordinateCreateResponse response =
                mapPlaceService.createCoordinateToken(
                        request.baseLatitude(),
                        request.baseLongitude(),
                        request.kakaoPlaceId(),
                        user.userId()
                );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/upload")
    @Operation(summary = "장소 업로드(토큰 기반)", description = "업로드 버튼 클릭 시 호출하여 이름/주소/이미지와 좌표 토큰으로 장소를 저장합니다. 카카오 장소 ID 없이도 좌표 기반 등록이 가능합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "장소 업로드 성공",
                    content = @Content(schema = @Schema(implementation = PlaceCreateResponse.class))
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
                                                "name": "장소 이름은 100자 이하여야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "좌표 토큰 만료/유효하지 않음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 좌표 토큰입니다.",
                                              "code": "PLACE_COORDINATE_TOKEN_INVALID"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<PlaceCreateResponse> upload(
            @Valid @RequestBody PlaceUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        PlaceCreateResponse response = mapPlaceService.uploadPlaceByToken(
                request.kakaoPlaceId(),
                request.name(),
                request.address(),
                request.category(),
                request.imageUrl(),
                request.coordinateToken(),
                user.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "장소 삭제", description = "지정한 장소 ID를 삭제합니다. 본인 소유 장소만 삭제 가능합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 삭제 성공",
                    content = @Content(
                            examples = @ExampleObject(value = "\"장소를 삭제했습니다.\"")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
                    description = "본인 소유가 아닌 장소 삭제 시도",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "자신의 장소만 삭제할 수 있습니다.",
                                              "code": "OTHERS_PLACE_NOT_DELETED"
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
    public ResponseEntity<String> delete(
            @Parameter(description = "삭제할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        mapPlaceService.deletePlace(placeId, user.userId());
        return ResponseEntity.ok("장소를 삭제했습니다.");
    }
}
