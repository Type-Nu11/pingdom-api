package com.typenull.pingdom.moderation.api.place;

import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Web 통합 신청의 관리자 심사 API입니다. */
@RestController
@RequiredArgsConstructor
@AdminOnly
@RequestMapping("/admin/merchant-place-applications")
@Tag(name = "Web", description = "웹 전용 API")
public class AdminMerchantPlaceApplicationController {
    private final MerchantPlaceApplicationService service;

    @GetMapping
    public MerchantPlaceApplicationPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.listAll(page, limit);
    }

    @GetMapping("/{id}")
    public MerchantPlaceApplicationResponse get(@PathVariable Long id) {
        return service.getAny(id);
    }

    @PostMapping("/{id}/approve")
    public MerchantPlaceApplicationResponse approve(
            @PathVariable Long id,
            @Valid @RequestBody MerchantPlaceApplicationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.approve(admin.userId(), id, request);
    }

    @PostMapping("/{id}/reject")
    public MerchantPlaceApplicationResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody MerchantPlaceApplicationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return service.reject(admin.userId(), id, request);
    }
}
