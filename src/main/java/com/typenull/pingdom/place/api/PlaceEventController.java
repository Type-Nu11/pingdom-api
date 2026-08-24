package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.event.PlaceEventDetailResponse;
import com.typenull.pingdom.place.api.dto.event.PlaceEventListResponse;

import com.typenull.pingdom.place.application.service.event.PlaceEventQueryService;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class PlaceEventController {

    private final PlaceEventQueryService placeEventQueryService;

    @GetMapping
    @Operation(
            summary = "공개 기간형 이벤트 목록 조회",
            description = "PUBLISHED 상태이며 종료 시각이 현재보다 이후인 이벤트를 시작 시각 순으로 조회합니다. "
                    + "기간 필터는 [fromAt, toAt)와 겹치는 이벤트에 적용됩니다. "
                    + "빈 목록은 events=[], totalCount=0, totalPages=0, hasNext=false로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 이벤트 목록 조회 성공", content = @Content(schema = @Schema(implementation = PlaceEventListResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "기간 또는 필터 조건이 올바르지 않음 (PLACE_EVENT_SEARCH_CONDITION_INVALID)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EVENT_SEARCH_CONDITION_INVALID",
                                    value = "{\"message\":\"이벤트 조회 기간 조건이 올바르지 않습니다.\",\"code\":\"PLACE_EVENT_SEARCH_CONDITION_INVALID\"}"
                            )
                    )
            )
    })
    public ResponseEntity<PlaceEventListResponse> listEvents(
            @Parameter(description = "이벤트 유형") @RequestParam(required = false) PlaceEventType eventType,
            @Parameter(description = "기간 겹침 조회 시작 시각. RFC 3339 offset을 받아 UTC로 정규화합니다.", example = "2026-08-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromAt,
            @Parameter(description = "기간 겹침 조회 종료 시각. fromAt보다 이후여야 하며 RFC 3339 offset을 받아 UTC로 정규화합니다.", example = "2026-09-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toAt,
            @Parameter(description = "페이지 번호. 1 미만은 1, 10000 초과는 10000으로 보정됩니다.", schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "10000", defaultValue = "1"), example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기. 1 미만은 1, 100 초과는 100으로 보정됩니다.", schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"), example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(placeEventQueryService.listDiscoverableEvents(
                eventType,
                toUtcLocalDateTime(fromAt),
                toUtcLocalDateTime(toAt),
                page,
                limit
        ));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "공개 기간형 이벤트 상세 조회", description = "종료·비공개·없는 이벤트는 모두 404 PLACE_EVENT_NOT_FOUND로 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 이벤트 상세 조회 성공", content = @Content(schema = @Schema(implementation = PlaceEventDetailResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "이벤트 ID 형식이 올바르지 않음 (PLACE_EVENT_SEARCH_CONDITION_INVALID)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EVENT_SEARCH_CONDITION_INVALID",
                                    value = "{\"message\":\"이벤트 조회 기간 조건이 올바르지 않습니다.\",\"code\":\"PLACE_EVENT_SEARCH_CONDITION_INVALID\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "이벤트를 찾을 수 없거나 공개 대상이 아님 (PLACE_EVENT_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EVENT_NOT_FOUND",
                                    value = "{\"message\":\"이벤트를 찾을 수 없습니다.\",\"code\":\"PLACE_EVENT_NOT_FOUND\"}"
                            )
                    )
            )
    })
    public ResponseEntity<PlaceEventDetailResponse> getEvent(
            @Parameter(description = "이벤트 ID", example = "1") @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(placeEventQueryService.getDiscoverableEvent(eventId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch() {
        return ResponseEntity.badRequest().body(ErrorResponse.from(MapErrorCode.PLACE_EVENT_SEARCH_CONDITION_INVALID));
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
