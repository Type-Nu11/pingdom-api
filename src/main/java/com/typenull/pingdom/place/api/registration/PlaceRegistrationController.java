package com.typenull.pingdom.place.api.registration;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationResponse;
import com.typenull.pingdom.place.application.service.registration.PlaceRegistrationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/place-registration-applications")
@Tag(name = "App", description = "앱 전용 API")
public class PlaceRegistrationController {
    private final PlaceRegistrationService service;

    @PostMapping
    @Operation(summary = "장소 등록 신청 생성")
    public ResponseEntity<PlaceRegistrationResponse> create(@Valid @RequestBody PlaceRegistrationRequest request,
                                                            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }
    @GetMapping
    @Operation(summary = "내 장소 등록 신청 목록 조회")
    public PlaceRegistrationPageResponse list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int limit,
                                              @CurrentUser JwtAuthenticatedUser user) {
        return service.list(user.userId(), page, limit);
    }
    @GetMapping("/{id}")
    @Operation(summary = "장소 등록 신청 상세 조회")
    public PlaceRegistrationResponse get(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.get(user.userId(), id); }
    @PutMapping("/{id}")
    @Operation(summary = "장소 등록 신청 수정")
    public PlaceRegistrationResponse update(@PathVariable Long id, @Valid @RequestBody PlaceRegistrationRequest request, @CurrentUser JwtAuthenticatedUser user) { return service.update(user.userId(), id, request); }
    @PostMapping("/{id}/submit")
    @Operation(summary = "장소 등록 신청 제출")
    public PlaceRegistrationResponse submit(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.submit(user.userId(), id); }
    @PostMapping("/{id}/cancel")
    @Operation(summary = "장소 등록 신청 취소")
    public PlaceRegistrationResponse cancel(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.cancel(user.userId(), id); }
    @PostMapping("/{id}/reopen")
    @Operation(summary = "장소 등록 신청 재개")
    public PlaceRegistrationResponse reopen(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.reopen(user.userId(), id); }
    @PostMapping("/{id}/complete")
    @Operation(summary = "장소 등록 신청 완료 처리")
    public PlaceRegistrationResponse complete(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.complete(user.userId(), id); }
}
