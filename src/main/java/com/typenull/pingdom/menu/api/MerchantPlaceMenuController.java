package com.typenull.pingdom.menu.api;

import com.typenull.pingdom.menu.api.dto.*;
import com.typenull.pingdom.menu.application.PlaceMenuService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/places/{placeId}/menus")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceMenuController {
    private final PlaceMenuService service;

    @PostMapping
    @Operation(summary = "장소 메뉴 등록")
    @ApiResponse(responseCode = "201", description = "메뉴 등록 성공")
    @ApiResponse(responseCode = "400", description = "메뉴 입력값 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "메뉴 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PlaceMenuResponse> create(@PathVariable Long placeId,
            @Valid @RequestBody PlaceMenuCreateRequest request, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), placeId, request));
    }

    @GetMapping
    @Operation(summary = "Merchant 장소 메뉴 목록 조회")
    public List<PlaceMenuResponse> list(@PathVariable Long placeId, @CurrentUser JwtAuthenticatedUser user) {
        return service.listOwned(user.userId(), placeId);
    }

    @GetMapping("/{menuId}")
    @Operation(summary = "Merchant 장소 메뉴 상세 조회")
    public PlaceMenuResponse get(@PathVariable Long placeId, @PathVariable Long menuId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.getOwned(user.userId(), placeId, menuId);
    }

    @PatchMapping("/{menuId}")
    @Operation(summary = "Merchant 장소 메뉴 수정")
    public PlaceMenuResponse update(@PathVariable Long placeId, @PathVariable Long menuId,
            @Valid @RequestBody PlaceMenuUpdateRequest request, @CurrentUser JwtAuthenticatedUser user) {
        return service.update(user.userId(), placeId, menuId, request);
    }

    @PatchMapping("/{menuId}/status")
    @Operation(summary = "Merchant 장소 메뉴 상태 변경")
    public PlaceMenuResponse changeStatus(@PathVariable Long placeId, @PathVariable Long menuId,
            @Valid @RequestBody PlaceMenuStatusRequest request, @CurrentUser JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), placeId, menuId, request.status());
    }

    @PatchMapping("/{menuId}/order")
    @Operation(summary = "Merchant 장소 메뉴 표시 순서 변경")
    public PlaceMenuResponse reorder(@PathVariable Long placeId, @PathVariable Long menuId,
            @Valid @RequestBody PlaceMenuOrderRequest request, @CurrentUser JwtAuthenticatedUser user) {
        return service.reorder(user.userId(), placeId, menuId, request);
    }

    @PostMapping("/{menuId}/deactivate")
    @Operation(summary = "Merchant 장소 메뉴 비활성화")
    public ResponseEntity<Void> deactivate(@PathVariable Long placeId, @PathVariable Long menuId,
            @CurrentUser JwtAuthenticatedUser user) {
        service.deactivate(user.userId(), placeId, menuId);
        return ResponseEntity.noContent().build();
    }
}
