package com.typenull.pingdom.place.api.registration;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationResponse;
import com.typenull.pingdom.place.application.service.registration.PlaceRegistrationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Web", description = "웹 전용 API")
public class PlaceRegistrationController {
    private final PlaceRegistrationService service;

    @PostMapping
    public ResponseEntity<PlaceRegistrationResponse> create(@Valid @RequestBody PlaceRegistrationRequest request,
                                                            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }
    @GetMapping
    public PlaceRegistrationPageResponse list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int limit,
                                              @CurrentUser JwtAuthenticatedUser user) {
        return service.list(user.userId(), page, limit);
    }
    @GetMapping("/{id}")
    public PlaceRegistrationResponse get(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.get(user.userId(), id); }
    @PutMapping("/{id}")
    public PlaceRegistrationResponse update(@PathVariable Long id, @Valid @RequestBody PlaceRegistrationRequest request, @CurrentUser JwtAuthenticatedUser user) { return service.update(user.userId(), id, request); }
    @PostMapping("/{id}/submit")
    public PlaceRegistrationResponse submit(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.submit(user.userId(), id); }
    @PostMapping("/{id}/cancel")
    public PlaceRegistrationResponse cancel(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.cancel(user.userId(), id); }
    @PostMapping("/{id}/reopen")
    public PlaceRegistrationResponse reopen(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.reopen(user.userId(), id); }
    @PostMapping("/{id}/complete")
    public PlaceRegistrationResponse complete(@PathVariable Long id, @CurrentUser JwtAuthenticatedUser user) { return service.complete(user.userId(), id); }
}
