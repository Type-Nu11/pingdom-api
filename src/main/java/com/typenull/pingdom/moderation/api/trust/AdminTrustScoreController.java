package com.typenull.pingdom.moderation.api.trust;

import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyItem;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyResolveRequest;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionEvaluationResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleItem;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleRequest;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleToggleResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreBatchResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreChangeHistoryItem;
import com.typenull.pingdom.moderation.application.query.trust.AdminTrustScoreQueryService;
import com.typenull.pingdom.moderation.application.service.trust.AdminTrustScoreService;
import com.typenull.pingdom.moderation.application.service.trust.TrustScoreBatchService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/admin/trust-score")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminTrustScoreController {

    private final AdminTrustScoreService adminTrustScoreService;
    private final AdminTrustScoreQueryService adminTrustScoreQueryService;
    private final TrustScoreBatchService trustScoreBatchService;

    @GetMapping("/reporters/{reporterUserId}")
    @Operation(
            summary = "신고자 신뢰 등급과 점수 근거 조회",
            description = "관리자가 신고자의 신뢰 점수, 등급, 신고 처리 통계 기반 점수 근거를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신뢰 등급과 점수 근거 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminTrustScoreResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "reporterUserId": 7,
                                              "reporterUsername": "pingdom_user",
                                              "trustScore": 80,
                                              "trustGrade": "HIGH",
                                              "restricted": false,
                                              "restrictedUntil": null,
                                              "restrictionReason": null,
                                              "evidence": {
                                                "submittedCount": 12,
                                                "acceptedCount": 8,
                                                "declinedCount": 4,
                                                "falseReportCount": 3,
                                                "acceptanceRate": 66.67,
                                                "baseScore": 100,
                                                "acceptedScoreBonus": 40,
                                                "falseReportScorePenalty": 60
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신고자 Trust Score 정책을 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "신고자 Trust Score 정책을 찾을 수 없습니다.",
                                              "code": "TRUST_SCORE_REPORTER_POLICY_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminTrustScoreResponse getTrustScore(
            @Parameter(description = "신고자 사용자 ID", example = "7") @PathVariable Long reporterUserId
    ) {
        return adminTrustScoreQueryService.getTrustScore(reporterUserId);
    }

    @PostMapping("/batch/recalculate")
    public ResponseEntity<AdminTrustScoreBatchResponse> recalculateTrustScores() {
        return ResponseEntity.ok(trustScoreBatchService.recalculate());
    }

    @GetMapping("/reporters/{reporterUserId}/history")
    public List<AdminTrustScoreChangeHistoryItem> listTrustScoreHistory(@PathVariable Long reporterUserId) {
        return trustScoreBatchService.listHistory(reporterUserId);
    }

    @GetMapping("/anomalies")
    @Operation(
            summary = "관리자 Trust Score 이상치 목록 조회",
            description = "관리자가 Trust Score 이상치 목록을 조회합니다. page는 1 이상, limit은 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Trust Score 이상치 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminTrustScoreAnomalyResponse.class))
            )
    })
    public AdminTrustScoreAnomalyResponse listAnomalies(
            @Parameter(description = "조회할 페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "신고자 사용자 ID 필터", example = "12")
            @RequestParam(required = false) Long reporterUserId,
            @Parameter(description = "미해결 이상치만 조회할지 여부", example = "true")
            @RequestParam(defaultValue = "false") boolean unresolvedOnly
    ) {
        return adminTrustScoreService.listAnomalies(page, limit, reporterUserId, unresolvedOnly);
    }

    @PatchMapping("/anomalies/{anomalyId}/resolve")
    @Operation(
            summary = "관리자 Trust Score 이상치 해결 처리",
            description = "관리자가 Trust Score 이상치를 해결 처리하고 감사 로그를 남깁니다."
    )
    public ResponseEntity<AdminTrustScoreAnomalyItem> resolveAnomaly(
            @PathVariable Long anomalyId,
            @Valid @RequestBody AdminTrustScoreAnomalyResolveRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminTrustScoreService.resolveAnomaly(anomalyId, request, adminUserId));
    }

    @GetMapping("/intervention-rules")
    @Operation(
            summary = "관리자 Trust Score 개입 규칙 목록 조회",
            description = "관리자가 Trust Score 개입 규칙 목록을 우선순위 순서로 조회합니다."
    )
    public AdminTrustScoreInterventionRuleResponse listRules(
            @Parameter(description = "활성화된 규칙만 조회할지 여부", example = "false")
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        return adminTrustScoreService.listRules(enabledOnly);
    }

    @PostMapping("/intervention-rules")
    @Operation(
            summary = "관리자 Trust Score 개입 규칙 생성",
            description = "관리자가 Trust Score 개입 규칙을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Trust Score 개입 규칙 생성 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminTrustScoreInterventionRuleItem.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "id": 1,
                                      "ruleName": "low trust temporary restriction",
                                      "triggerType": "FALSE_REPORT_COUNT",
                                      "actionType": "TEMPORARY_RESTRICT",
                                      "enabled": true,
                                      "minTrustScore": 0,
                                      "maxTrustScore": 60,
                                      "minSubmittedCount": 3,
                                      "minFalseReportCount": 3,
                                      "durationDays": 7,
                                      "priority": 10,
                                      "reason": "허위 신고 누적 사용자 제한"
                                    }
                                    """)
                    )
            )
    })
    public ResponseEntity<AdminTrustScoreInterventionRuleItem> createRule(
            @Valid @RequestBody AdminTrustScoreInterventionRuleRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminTrustScoreService.createRule(request, adminUserId));
    }

    @PutMapping("/intervention-rules/{ruleId}")
    @Operation(
            summary = "관리자 Trust Score 개입 규칙 수정",
            description = "관리자가 Trust Score 개입 규칙 내용을 수정합니다."
    )
    public ResponseEntity<AdminTrustScoreInterventionRuleItem> updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody AdminTrustScoreInterventionRuleRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminTrustScoreService.updateRule(ruleId, request, adminUserId));
    }

    @PatchMapping("/intervention-rules/{ruleId}/enable")
    @Operation(
            summary = "관리자 Trust Score 개입 규칙 활성화",
            description = "관리자가 Trust Score 개입 규칙을 활성화합니다."
    )
    public ResponseEntity<AdminTrustScoreInterventionRuleToggleResponse> enableRule(
            @PathVariable Long ruleId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminTrustScoreService.enableRule(ruleId, adminUserId));
    }

    @PatchMapping("/intervention-rules/{ruleId}/disable")
    @Operation(
            summary = "관리자 Trust Score 개입 규칙 비활성화",
            description = "관리자가 Trust Score 개입 규칙을 비활성화합니다."
    )
    public ResponseEntity<AdminTrustScoreInterventionRuleToggleResponse> disableRule(
            @PathVariable Long ruleId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminTrustScoreService.disableRule(ruleId, adminUserId));
    }

    @PostMapping("/reporters/{reporterUserId}/interventions/evaluate")
    @Operation(
            summary = "관리자 Trust Score 개입 규칙 수동 평가",
            description = "관리자가 신고자의 현재 Trust Score 정책에 활성 개입 규칙을 수동 평가합니다."
    )
    public ResponseEntity<AdminTrustScoreInterventionEvaluationResponse> evaluateReporter(
            @PathVariable Long reporterUserId
    ) {
        return ResponseEntity.ok(adminTrustScoreService.evaluateReporter(reporterUserId));
    }
}
