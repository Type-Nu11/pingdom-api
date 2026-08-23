package com.typenull.pingdom.product.api;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.product.api.dto.ReservableProductCreateRequest;
import com.typenull.pingdom.product.api.dto.ReservableProductResponse;
import com.typenull.pingdom.product.application.ReservableProductService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/reservable-products")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantReservableProductController {
    private final ReservableProductService service;

    @PostMapping
    @Operation(summary = "예약 상품 등록")
    @ApiResponse(responseCode = "201", description = "예약 상품 등록 성공")
    public ResponseEntity<ReservableProductResponse> create(
            @Valid @RequestBody ReservableProductCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 예약 상품 목록 조회")
    public List<ReservableProductResponse> list(
            @CurrentUser JwtAuthenticatedUser user) {
        return service.listOwned(user.userId());
    }

    @PostMapping("/{productId}/activate")
    @Operation(summary = "예약 상품 활성화")
    public ReservableProductResponse activate(@PathVariable Long productId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), productId, true);
    }

    @PostMapping("/{productId}/deactivate")
    @Operation(summary = "예약 상품 비활성화")
    public ReservableProductResponse deactivate(@PathVariable Long productId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), productId, false);
    }
}
