package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.domain.map.dto.PlaceCreateRequest;
import com.typenull.pingdom.domain.map.dto.PlaceCreateResponse;
import com.typenull.pingdom.domain.map.service.MapPlaceService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/map/places") // 경로 단순화: /map/places/create -> /map/places
@RequiredArgsConstructor
public class MapPlaceController {

    private final MapPlaceService mapPlaceService;

    @PostMapping
    @Operation(summary = "장소 업로드", description = "사용자가 지도에 표시할 장소를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "장소 등록 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "id": 1,
                              "name": "테스트 장소",
                              "address": "서울특별시 강남구 테헤란로 1",
                              "latitude": 37.501,
                              "longitude": 127.039,
                              "message": "장소를 저장했습니다."
                            }
                            """)))
    })
    public ResponseEntity<PlaceCreateResponse> create(
            @Valid @RequestBody PlaceCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long placeId = mapPlaceService.createPlace(request, user.userId());
        // 201 Created 응답
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlaceCreateResponse(
                        placeId,
                        request.name(),
                        request.address(),
                        request.latitude(),
                        request.longitude(),
                        "장소를 저장했습니다."
                ));
    }

    @DeleteMapping("/{id}") // 경로 단순화: /map/places/{id}/delete -> /map/places/{id}
    @Operation(summary = "장소 삭제", description = "지정한 장소 ID를 삭제합니다. 본인 소유 장소만 삭제 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "장소 삭제 성공 (반환 값 없음)"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "삭제할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        mapPlaceService.deletePlace(placeId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
