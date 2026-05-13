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
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MapPlaceController {

    private final MapPlaceService mapPlaceService;

    @PostMapping("/places/create")
    @Operation(summary = "장소 업로드", description = "사용자가 지도에 표시할 장소를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "장소 등록 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "id": 1,
                              "name": "카페",
                              "address": "서울특별시 ...",
                              "latitude": 37.5665,
                              "longitude": 126.978
                            }
                            """)))
    })
    public ResponseEntity<PlaceCreateResponse> create(
            @Valid @RequestBody PlaceCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        PlaceCreateResponse response = mapPlaceService.createPlace(request, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/places/{id}/delete")
    @Operation(summary = "장소 삭제", description = "지정한 장소 ID를 삭제합니다. 본인 소유 장소만 삭제 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "장소 삭제 성공 (반환 값 없음)"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<String> delete(
            @Parameter(description = "삭제할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        mapPlaceService.deletePlace(placeId, user.userId());
        return ResponseEntity.ok("장소를 삭제했습니다.");
    }
}
