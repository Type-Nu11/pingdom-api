package com.typenull.pingdom.identity.api.travel;

import com.typenull.pingdom.identity.api.dto.travel.CurrentActivityIntentResponse;
import com.typenull.pingdom.identity.api.dto.travel.CurrentActivityIntentUpdateRequest;
import com.typenull.pingdom.identity.api.dto.travel.TravelScheduleCreateRequest;
import com.typenull.pingdom.identity.api.dto.travel.TravelScheduleListResponse;
import com.typenull.pingdom.identity.api.dto.travel.TravelScheduleResponse;
import com.typenull.pingdom.identity.api.dto.travel.TravelScheduleUpdateRequest;
import com.typenull.pingdom.identity.application.service.travel.CurrentActivityIntentService;
import com.typenull.pingdom.identity.application.service.travel.TravelScheduleService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class UserTravelController {

    private final TravelScheduleService travelScheduleService;
    private final CurrentActivityIntentService currentActivityIntentService;

    @GetMapping("/travel-schedules")
    @Operation(summary = "내 여행 일정 목록 조회", description = "현재 인증된 사용자의 여행 일정을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = TravelScheduleListResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TravelScheduleListResponse> getTravelSchedules(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = authenticatedUserId(user);
        LocalDate today = travelScheduleService.today();
        return ResponseEntity.ok(new TravelScheduleListResponse(
                travelScheduleService.getSchedules(userId).stream()
                        .map(schedule -> TravelScheduleResponse.from(schedule, today))
                        .toList()
        ));
    }

    @PostMapping("/travel-schedules")
    @Operation(summary = "여행 일정 생성", description = "현재 인증된 사용자에게 날짜 범위의 여행 일정을 추가합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공", content = @Content(schema = @Schema(implementation = TravelScheduleResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TravelScheduleResponse> createTravelSchedule(
            @Valid @RequestBody TravelScheduleCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        var schedule = travelScheduleService.create(
                authenticatedUserId(user),
                request.startDate(),
                request.endDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TravelScheduleResponse.from(schedule, travelScheduleService.today()));
    }

    @PatchMapping("/travel-schedules/{scheduleId}")
    @Operation(summary = "여행 일정 기간 변경", description = "취소되지 않은 본인 여행 일정의 날짜 범위를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공", content = @Content(schema = @Schema(implementation = TravelScheduleResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "일정을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "취소된 일정 또는 동시 수정 충돌", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TravelScheduleResponse> updateTravelSchedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody TravelScheduleUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        var schedule = travelScheduleService.update(
                authenticatedUserId(user),
                scheduleId,
                request.startDate(),
                request.endDate()
        );
        return ResponseEntity.ok(TravelScheduleResponse.from(schedule, travelScheduleService.today()));
    }

    @PostMapping("/travel-schedules/{scheduleId}/cancel")
    @Operation(summary = "여행 일정 취소", description = "본인 여행 일정을 취소 상태로 전환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공", content = @Content(schema = @Schema(implementation = TravelScheduleResponse.class))),
            @ApiResponse(responseCode = "404", description = "일정을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "다른 요청으로 일정이 변경됨", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TravelScheduleResponse> cancelTravelSchedule(
            @PathVariable Long scheduleId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        var schedule = travelScheduleService.cancel(authenticatedUserId(user), scheduleId);
        return ResponseEntity.ok(TravelScheduleResponse.from(schedule, travelScheduleService.today()));
    }

    @GetMapping("/current-activity-intent")
    @Operation(summary = "현재 행동 의도 조회", description = "만료되지 않은 현재 행동 의도를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = CurrentActivityIntentResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CurrentActivityIntentResponse> getCurrentActivityIntent(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(CurrentActivityIntentResponse.from(
                currentActivityIntentService.getCurrentIntent(authenticatedUserId(user))
        ));
    }

    @PutMapping("/current-activity-intent")
    @Operation(summary = "현재 행동 의도 변경", description = "행동 의도를 2시간 동안 유지하도록 설정하거나 갱신합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공", content = @Content(schema = @Schema(implementation = CurrentActivityIntentResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CurrentActivityIntentResponse> replaceCurrentActivityIntent(
            @Valid @RequestBody CurrentActivityIntentUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(CurrentActivityIntentResponse.from(
                currentActivityIntentService.replace(authenticatedUserId(user), request.intent())
        ));
    }

    @DeleteMapping("/current-activity-intent")
    @Operation(summary = "현재 행동 의도 해제", description = "현재 행동 의도를 즉시 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> clearCurrentActivityIntent(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        currentActivityIntentService.clear(authenticatedUserId(user));
        return ResponseEntity.noContent().build();
    }

    private Long authenticatedUserId(JwtAuthenticatedUser user) {
        if (user == null) {
            throw new com.typenull.pingdom.identity.domain.exception.AuthException(
                    com.typenull.pingdom.identity.domain.exception.AuthErrorCode.INVALID_TOKEN
            );
        }
        return user.userId();
    }
}
