package com.typenull.pingdom.moderation.api.outbox;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventItem;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventResponse;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventRetryRequest;
import com.typenull.pingdom.moderation.application.query.outbox.AdminOutboxEventQueryService;
import com.typenull.pingdom.moderation.application.service.outbox.AdminOutboxEventRecoveryService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/outbox-events")
@RequiredArgsConstructor
@AdminOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminOutboxEventController {

    private final AdminOutboxEventQueryService queryService;
    private final AdminOutboxEventRecoveryService recoveryService;

    @GetMapping
    @Operation(
            summary = "관리자 Outbox 이벤트 조회",
            description = "운영자가 Outbox 이벤트의 상태와 실패 원인을 조회합니다. 민감한 payload와 deduplication key는 반환하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "조회 기간 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Outbox 복구 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminOutboxEventResponse list(
            @Parameter(description = "처리 상태", example = "FAILED")
            @RequestParam(required = false) OutboxEventStatus status,
            @Parameter(description = "이벤트 유형", example = "EMAIL_VERIFICATION_REQUESTED")
            @RequestParam(required = false) OutboxEventType eventType,
            @Parameter(description = "aggregate 유형", example = "USER")
            @RequestParam(required = false) String aggregateType,
            @Parameter(description = "aggregate ID", example = "10")
            @RequestParam(required = false) String aggregateId,
            @Parameter(description = "생성 시각 조회 시작")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "생성 시각 조회 종료")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return queryService.list(
                admin.userId(),
                status,
                eventType,
                aggregateType,
                aggregateId,
                from,
                to,
                page,
                limit
        );
    }

    @PostMapping("/{eventId}/retry")
    @Operation(
            summary = "관리자 Outbox 이벤트 재처리",
            description = "장애 원인과 중복 처리 안전성을 확인한 뒤 최종 실패 이벤트를 재처리 대상으로 전환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재처리 예약 성공"),
            @ApiResponse(responseCode = "400", description = "재처리 사유 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Outbox 복구 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "이벤트 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "재처리 불가 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminOutboxEventItem retry(
            @PathVariable String eventId,
            @Valid @RequestBody AdminOutboxEventRetryRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return recoveryService.retry(admin.userId(), eventId, request.reason());
    }
}
