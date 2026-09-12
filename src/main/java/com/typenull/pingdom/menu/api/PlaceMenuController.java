package com.typenull.pingdom.menu.api;

import com.typenull.pingdom.menu.api.dto.PlaceMenuPublicResponse;
import com.typenull.pingdom.menu.application.PlaceMenuService;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
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
            description = "AVAILABLE와 SOLD_OUT 메뉴만 displayOrder 오름차순으로 반환합니다. 로그인 사용자는 국가 코드에 따른 참고 환산 가격을 함께 받습니다. 예를 들어 원래 가격이 KRW 9,000원인 메뉴는 USD 6.43으로 환산될 수 있으며, 이는 가정된 환율 예시입니다. 원래 통화와 표시 통화가 같으면 convertedPrice는 null이고, 실제 결제 기준은 항상 원래 가격과 통화입니다.")
    @ApiResponse(responseCode = "200", description = "공개 메뉴 목록 조회 성공")
    @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = com.typenull.pingdom.shared.api.dto.ErrorResponse.class)))
    public ResponseEntity<List<PlaceMenuPublicResponse>> list(
            @PathVariable Long placeId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.listPublic(placeId, user == null ? null : user.userId()));
    }
}
