package com.typenull.pingdom.product.api;

import com.typenull.pingdom.product.api.dto.ReservableProductCreateRequest;
import com.typenull.pingdom.product.api.dto.ReservableProductResponse;
import com.typenull.pingdom.product.application.ReservableProductService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/reservable-products")
@RequiredArgsConstructor
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantReservableProductController {
    private final ReservableProductService service;

    @PostMapping
    @Operation(summary = "예약 상품 등록")
    public ResponseEntity<ReservableProductResponse> create(
            @Valid @RequestBody ReservableProductCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 예약 상품 목록 조회")
    public List<ReservableProductResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.listOwned(user.userId());
    }

    @PostMapping("/{productId}/activate")
    @Operation(summary = "예약 상품 활성화")
    public ReservableProductResponse activate(@PathVariable Long productId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), productId, true);
    }

    @PostMapping("/{productId}/deactivate")
    @Operation(summary = "예약 상품 비활성화")
    public ReservableProductResponse deactivate(@PathVariable Long productId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), productId, false);
    }
}
