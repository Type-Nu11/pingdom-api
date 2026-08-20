package com.typenull.pingdom.place.api.registration;

import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationRequest;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Web에서 사업자와 장소를 함께 신청하는 API입니다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/merchant-place-applications")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceApplicationController {
    private final MerchantPlaceApplicationService service;

    @PostMapping
    public ResponseEntity<MerchantPlaceApplicationResponse> create(
            @Valid @RequestBody MerchantPlaceApplicationRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping
    public MerchantPlaceApplicationPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.list(user.userId(), page, limit);
    }

    @GetMapping("/{id}")
    public MerchantPlaceApplicationResponse get(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.get(user.userId(), id);
    }

    @PutMapping("/{id}")
    public MerchantPlaceApplicationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MerchantPlaceApplicationRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.update(user.userId(), id, request);
    }

    @PostMapping("/{id}/submit")
    public MerchantPlaceApplicationResponse submit(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.submit(user.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    public MerchantPlaceApplicationResponse cancel(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.cancel(user.userId(), id);
    }

    @PostMapping("/{id}/reopen")
    public MerchantPlaceApplicationResponse reopen(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.reopen(user.userId(), id);
    }
}
