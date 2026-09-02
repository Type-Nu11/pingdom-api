package com.typenull.pingdom.menu.api;

import com.typenull.pingdom.menu.api.dto.PlaceMenuResponse;
import com.typenull.pingdom.menu.application.PlaceMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/places/{placeId}/menus")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PlaceMenuController {
    private final PlaceMenuService service;

    @GetMapping
    @Operation(summary = "관광객용 장소 메뉴 목록 조회",
            description = "AVAILABLE와 SOLD_OUT 메뉴만 displayOrder 오름차순으로 반환합니다. 메뉴가 없으면 빈 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "공개 메뉴 목록 조회 성공")
    @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = com.typenull.pingdom.shared.api.dto.ErrorResponse.class)))
    public ResponseEntity<List<PlaceMenuResponse>> list(@PathVariable Long placeId) {
        return ResponseEntity.ok(service.listPublic(placeId));
    }
}
