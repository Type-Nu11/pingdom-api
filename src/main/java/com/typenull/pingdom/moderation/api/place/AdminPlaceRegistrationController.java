package com.typenull.pingdom.moderation.api.place;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationReviewRequest;
import com.typenull.pingdom.place.application.service.registration.PlaceRegistrationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/place-registration-applications")
@AdminOnly
public class AdminPlaceRegistrationController {
    private final PlaceRegistrationService service;
    @GetMapping
    public PlaceRegistrationPageResponse list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) { return service.listAll(page, limit); }
    @GetMapping("/{id}")
    public PlaceRegistrationResponse get(@PathVariable Long id) { return service.getAny(id); }
    @PostMapping("/{id}/approve")
    public PlaceRegistrationResponse approve(@PathVariable Long id, @Valid @RequestBody PlaceRegistrationReviewRequest request, @CurrentUser JwtAuthenticatedUser admin) { return service.approve(admin.userId(), id, request); }
    @PostMapping("/{id}/reject")
    public PlaceRegistrationResponse reject(@PathVariable Long id, @Valid @RequestBody PlaceRegistrationReviewRequest request, @CurrentUser JwtAuthenticatedUser admin) { return service.reject(admin.userId(), id, request); }
}
