package com.typenull.pingdom.privacy.api;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.privacy.api.dto.PrivacyProcessingHistoryResponse;

import com.typenull.pingdom.privacy.application.PrivacyProcessingHistoryQueryService;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/privacy-processing-histories")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminPrivacyProcessingHistoryController {

    private final PrivacyProcessingHistoryQueryService queryService;

    @GetMapping
    @Operation(
            summary = "개인정보 처리 이력 조회",
            description = "관리자가 개인정보 export, 탈퇴, 익명화, 삭제 처리 이력을 페이지 단위로 조회합니다."
    )
    public PrivacyProcessingHistoryResponse listHistories(
            @Parameter(description = "처리 대상 사용자 ID", example = "1")
            @RequestParam(required = false) Long subjectUserId,
            @Parameter(description = "처리 수행 사용자 ID", example = "1")
            @RequestParam(required = false) Long actorUserId,
            @Parameter(description = "처리 유형", example = "EXPORT_REQUESTED")
            @RequestParam(required = false) PrivacyProcessingAction action,
            @Parameter(description = "조회 시작 시각", example = "2026-07-01T00:00:00")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-07-31T23:59:59")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return queryService.listHistories(subjectUserId, actorUserId, action, from, to, page, limit);
    }
}
