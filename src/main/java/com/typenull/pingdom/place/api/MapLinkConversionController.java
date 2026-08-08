package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.conversion.MapLinkConversionRequest;
import com.typenull.pingdom.place.application.service.conversion.MapLinkConversionEventService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/places/{placeId}/map-link-conversions")
@RequiredArgsConstructor
public class MapLinkConversionController {
    private final MapLinkConversionEventService service;

    @PostMapping
    public ResponseEntity<Void> record(@PathVariable long placeId,
                                       @Valid @RequestBody MapLinkConversionRequest request,
                                       @AuthenticationPrincipal JwtAuthenticatedUser user) {
        service.record(user.userId(), placeId, request.linkType(), request.provider(), request.requestId(), LocalDateTime.now());
        return ResponseEntity.noContent().build();
    }
}
