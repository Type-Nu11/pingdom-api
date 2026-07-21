package com.typenull.pingdom.moderation.api.place.information;

import com.typenull.pingdom.place.application.service.place.information.PlaceInformationReverificationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/admin/place-information-reverification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlaceInformationReverificationMaintenanceController {
    private final PlaceInformationReverificationService service;

    @PostMapping("/expire-due")
    public int expireDue(@AuthenticationPrincipal JwtAuthenticatedUser admin) {
        return service.expireDue(admin == null ? null : admin.userId());
    }
}
