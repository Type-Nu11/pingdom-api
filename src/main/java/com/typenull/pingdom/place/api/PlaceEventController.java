package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.event.PlaceEventDetailResponse;
import com.typenull.pingdom.place.api.dto.event.PlaceEventListResponse;
import com.typenull.pingdom.place.application.service.event.PlaceEventQueryService;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PlaceEventController {

    private final PlaceEventQueryService placeEventQueryService;

    @GetMapping
    @Operation(summary = "공개 기간형 이벤트 목록 조회", description = "종료되지 않은 공개 이벤트를 시작 시각 순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "공개 이벤트 목록 조회 성공", content = @Content(schema = @Schema(implementation = PlaceEventListResponse.class)))
    public ResponseEntity<PlaceEventListResponse> listEvents(
            @Parameter(description = "이벤트 유형") @RequestParam(required = false) PlaceEventType eventType,
            @Parameter(description = "기간 겹침 조회 시작 시각", example = "2026-08-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromAt,
            @Parameter(description = "기간 겹침 조회 종료 시각", example = "2026-09-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toAt,
            @Parameter(description = "페이지 번호", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(placeEventQueryService.listDiscoverableEvents(eventType, fromAt, toAt, page, limit));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "공개 기간형 이벤트 상세 조회", description = "종료되지 않은 공개 이벤트의 장소와 기간 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 이벤트 상세 조회 성공", content = @Content(schema = @Schema(implementation = PlaceEventDetailResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "이벤트를 찾을 수 없거나 공개 대상이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PlaceEventDetailResponse> getEvent(
            @Parameter(description = "이벤트 ID", example = "1") @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(placeEventQueryService.getDiscoverableEvent(eventId));
    }
}
