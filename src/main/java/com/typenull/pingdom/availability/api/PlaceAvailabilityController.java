package com.typenull.pingdom.availability.api;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/places/{placeId}/availabilities")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PlaceAvailabilityController {
    private final PlaceAvailabilityService service;

    @GetMapping
    @Operation(summary = "장소 예약 가능 시간 조회")
    public List<AvailabilityResponse> list(@PathVariable Long placeId) {
        return service.listPublic(placeId);
    }
}
