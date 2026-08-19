package com.typenull.pingdom.moderation.api.place.event;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventActionRequest;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventRequest;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventResponse;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventListResponse;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventListItem;
import com.typenull.pingdom.moderation.application.service.place.event.AdminPlaceEventService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Min;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;

@RestController
@RequestMapping("/admin/place-events")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceEventController {

    private final AdminPlaceEventService adminPlaceEventService;

    @GetMapping
    @Operation(summary = "관리자 기간형 이벤트 목록 조회")
    public AdminPlaceEventListResponse listEvents(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) PlaceEventType eventType,
            @RequestParam(required = false) PlaceEventPublicationStatus publicationStatus,
            @RequestParam(required = false) PlaceEventScheduleStatus scheduleStatus) {
        return adminPlaceEventService.list(keyword, placeId, eventType, publicationStatus, scheduleStatus, page, limit);
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "관리자 기간형 이벤트 상세 조회")
    public AdminPlaceEventListItem getEvent(@PathVariable Long eventId) {
        return adminPlaceEventService.get(eventId);
    }

    @PostMapping
    @Operation(summary = "관리자 기간형 이벤트 등록", description = "장소와 연결된 기간형 이벤트를 초안으로 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "이벤트 초안 등록 성공", content = @Content(schema = @Schema(implementation = AdminPlaceEventResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 또는 기간 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminPlaceEventResponse> createEvent(
            @Valid @RequestBody AdminPlaceEventRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPlaceEventService.create(adminUserId(adminUser), request));
    }

    @PatchMapping("/{eventId}")
    @Operation(summary = "관리자 기간형 이벤트 수정", description = "초안 상태의 기간형 이벤트 정보와 연결 장소를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이벤트 수정 성공", content = @Content(schema = @Schema(implementation = AdminPlaceEventResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 또는 기간 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "이벤트 또는 장소를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "초안이 아닌 이벤트 수정 시도", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminPlaceEventResponse> updateEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody AdminPlaceEventRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.ok(adminPlaceEventService.update(adminUserId(adminUser), eventId, request));
    }

    @PostMapping("/{eventId}/publish")
    @Operation(summary = "관리자 기간형 이벤트 공개", description = "종료되지 않은 초안 이벤트를 앱 탐색에 공개합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이벤트 공개 성공", content = @Content(schema = @Schema(implementation = AdminPlaceEventResponse.class))),
            @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "공개할 수 없는 상태 또는 기간", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminPlaceEventResponse> publishEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody AdminPlaceEventActionRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.ok(adminPlaceEventService.publish(adminUserId(adminUser), eventId, request));
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(summary = "관리자 기간형 이벤트 취소", description = "초안 또는 공개된 기간형 이벤트를 취소하고 앱 탐색에서 제외합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이벤트 취소 성공", content = @Content(schema = @Schema(implementation = AdminPlaceEventResponse.class))),
            @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 취소된 이벤트", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminPlaceEventResponse> cancelEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody AdminPlaceEventActionRequest request,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return ResponseEntity.ok(adminPlaceEventService.cancel(adminUserId(adminUser), eventId, request));
    }

    private Long adminUserId(JwtAuthenticatedUser adminUser) {
        return adminUser == null ? null : adminUser.userId();
    }
}
