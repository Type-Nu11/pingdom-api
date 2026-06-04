package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.PlaceListResponse;
import com.typenull.pingdom.place.application.service.PlaceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/place")
@RequiredArgsConstructor
@Tag(name = "App Place", description = "앱용 장소 조회 API")
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    @GetMapping
    @Operation(summary = "장소 목록 조회", description = "앱에서 사용할 장소 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceListResponse.class))
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
    public ResponseEntity<PlaceListResponse> listPlaces(
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "장소명 검색어", example = "카페")
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(placeQueryService.listPlaces(page, limit, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "장소 상세 조회", description = "특정 장소의 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceDetailResponse.class))
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
    public ResponseEntity<PlaceDetailResponse> getPlace(
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable("id") Long placeId
    ) {
        return ResponseEntity.ok(placeQueryService.getPlace(placeId));
    }
}
