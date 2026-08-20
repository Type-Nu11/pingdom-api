package com.typenull.pingdom.boost.api;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductCreateRequest;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductResponse;
import com.typenull.pingdom.boost.application.VerifiedBoostProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/verified-boost-products")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminVerifiedBoostProductController {

    private final VerifiedBoostProductService service;

    @PostMapping
    @Operation(summary = "관리자 Verified Boost 상품 생성")
    public ResponseEntity<VerifiedBoostProductResponse> create(
            @Valid @RequestBody VerifiedBoostProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @Operation(summary = "관리자 Verified Boost 상품 목록 조회")
    public VerifiedBoostProductPageResponse list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return service.list(page, limit);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "관리자 Verified Boost 상품 상세 조회")
    public VerifiedBoostProductResponse get(@PathVariable Long productId) {
        return service.get(productId);
    }

    @PostMapping("/{productId}/activate")
    @Operation(summary = "Verified Boost 상품 활성화")
    public VerifiedBoostProductResponse activate(@PathVariable Long productId) {
        return service.activate(productId);
    }

    @PostMapping("/{productId}/deactivate")
    @Operation(summary = "Verified Boost 상품 비활성화")
    public VerifiedBoostProductResponse deactivate(@PathVariable Long productId) {
        return service.deactivate(productId);
    }
}
