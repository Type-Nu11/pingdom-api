package com.typenull.pingdom.place.api.registration;

import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationRequest;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Merchant 장소 신청 생성")
    public ResponseEntity<MerchantPlaceApplicationResponse> create(
            @Valid @RequestBody MerchantPlaceApplicationRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 Merchant 장소 신청 목록 조회")
    public MerchantPlaceApplicationPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.list(user.userId(), page, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Merchant 장소 신청 상세 조회",
            description = "NEW_PLACE이면 newPlace에 저장된 장소 입력값 전체를 반환합니다. 첨부파일은 별도 첨부 API로 조회·관리합니다.")
    public MerchantPlaceApplicationResponse get(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.get(user.userId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Merchant 장소 신청 수정")
    public MerchantPlaceApplicationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MerchantPlaceApplicationRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return service.update(user.userId(), id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Merchant 장소 신청 제출")
    public MerchantPlaceApplicationResponse submit(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.submit(user.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Merchant 장소 신청 취소")
    public MerchantPlaceApplicationResponse cancel(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.cancel(user.userId(), id);
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Merchant 장소 신청 재개")
    public MerchantPlaceApplicationResponse reopen(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) {
        return service.reopen(user.userId(), id);
    }
}
