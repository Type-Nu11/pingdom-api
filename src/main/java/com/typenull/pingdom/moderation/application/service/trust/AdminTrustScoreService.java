package com.typenull.pingdom.moderation.application.service.trust;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreAnomalyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreInterventionRuleRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyItem;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyResolveRequest;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionEvaluationResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleItem;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleRequest;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleToggleResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고자 점수 이상 징후의 해결, 개입 규칙 관리·수동 평가와 감사 기록 저장.
 * 규칙 평가는 저장된 점수·집계를 사용하며 원본 신고 기반 점수 재산출은 처리 범위에서 제외.
 */
@Service
@RequiredArgsConstructor
public class AdminTrustScoreService {

    private static final int MAX_LIMIT = 100;
    private static final String NO_MATCHED_RULE_MESSAGE = "적용 가능한 Trust Score 개입 규칙이 없습니다.";
    private static final String EVALUATED_MESSAGE = "Trust Score 개입 규칙을 적용했습니다.";

    private final TrustScoreAnomalyRepository trustScoreAnomalyRepository;
    private final TrustScoreInterventionRuleRepository trustScoreInterventionRuleRepository;
    private final ReporterModerationPolicyRepository reporterModerationPolicyRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    /**
     * 신고자 ID와 미해결 여부를 선택적으로 적용해 이상 징후를 탐지 시각·ID 내림차순으로 반환. page는 1 이상·limit는 1~100으로 보정.
     */
    @Transactional(readOnly = true)
    public AdminTrustScoreAnomalyResponse listAnomalies(int page, int limit, Long reporterUserId, boolean unresolvedOnly) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        PageRequest pageable = PageRequest.of(safePage - 1, safeLimit);

        Page<TrustScoreAnomaly> anomalyPage;
        if (reporterUserId != null && unresolvedOnly) {
            anomalyPage = trustScoreAnomalyRepository
                    .findAllByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(reporterUserId, pageable);
        } else if (reporterUserId != null) {
            anomalyPage = trustScoreAnomalyRepository.findAllByReporterUserIdOrderByDetectedAtDescIdDesc(reporterUserId, pageable);
        } else if (unresolvedOnly) {
            anomalyPage = trustScoreAnomalyRepository.findAllByResolvedAtIsNullOrderByDetectedAtDescIdDesc(pageable);
        } else {
            anomalyPage = trustScoreAnomalyRepository.findAllByOrderByDetectedAtDescIdDesc(pageable);
        }

        List<AdminTrustScoreAnomalyItem> anomalies = anomalyPage.getContent().stream()
                .map(AdminTrustScoreAnomalyItem::from)
                .toList();

        return new AdminTrustScoreAnomalyResponse(
                anomalies,
                safePage,
                safeLimit,
                anomalyPage.getTotalElements(),
                anomalyPage.getTotalPages()
        );
    }

    /**
     * 저장된 이상 징후에 현재 해결 시각·사유를 반영하고 감사 기록을 같은 트랜잭션에 저장.
     * 이상 징후 부재 시 TRUST_SCORE_ANOMALY_NOT_FOUND. 신고자 점수·제한 정책은 유지.
     */
    @Transactional
    public AdminTrustScoreAnomalyItem resolveAnomaly(
            Long anomalyId,
            AdminTrustScoreAnomalyResolveRequest request,
            Long adminUserId
    ) {
        TrustScoreAnomaly anomaly = trustScoreAnomalyRepository.findById(anomalyId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TRUST_SCORE_ANOMALY_NOT_FOUND));
        AdminTrustScoreAnomalyItem beforeState = AdminTrustScoreAnomalyItem.from(anomaly);

        anomaly.resolve(LocalDateTime.now(clock), request.resolutionReason());

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.TRUST_SCORE_ANOMALY_RESOLVED,
                AdminAuditTargetType.TRUST_SCORE_ANOMALY,
                anomaly.getId(),
                request.resolutionReason(),
                beforeState,
                AdminTrustScoreAnomalyItem.from(anomaly)
        );

        return AdminTrustScoreAnomalyItem.from(anomaly);
    }

    /**
     * enabledOnly이면 활성 규칙, 아니면 전체 규칙을 priority·ID 오름차순으로 반환하는 규칙 정의 조회 전용 메서드.
     */
    @Transactional(readOnly = true)
    public AdminTrustScoreInterventionRuleResponse listRules(boolean enabledOnly) {
        List<TrustScoreInterventionRule> rules = enabledOnly
                ? trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc()
                : trustScoreInterventionRuleRepository.findAllByOrderByPriorityAscIdAsc();

        return new AdminTrustScoreInterventionRuleResponse(rules.stream()
                .map(AdminTrustScoreInterventionRuleItem::from)
                .toList());
    }

    /**
     * 최소·최대 점수 순서와 조치별 기간을 검증하고 이름이 중복되지 않는 활성 규칙을 신규 생성.
     * 임시 제한은 1~365일만 허용하고 다른 조치는 기간을 받지 않으며 규칙과 감사 기록을 같은 트랜잭션에 저장.
     */
    @Transactional
    public AdminTrustScoreInterventionRuleItem createRule(
            AdminTrustScoreInterventionRuleRequest request,
            Long adminUserId
    ) {
        validateRuleRequest(request);
        if (trustScoreInterventionRuleRepository.existsByRuleName(request.ruleName())) {
            throw new AdminException(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_DUPLICATED);
        }

        TrustScoreInterventionRule rule = trustScoreInterventionRuleRepository.save(TrustScoreInterventionRule.builder()
                .ruleName(request.ruleName())
                .triggerType(request.triggerType())
                .actionType(request.actionType())
                .enabled(true)
                .minTrustScore(request.minTrustScore())
                .maxTrustScore(request.maxTrustScore())
                .minSubmittedCount(request.minSubmittedCount())
                .minFalseReportCount(request.minFalseReportCount())
                .durationDays(request.durationDays())
                .priority(request.priority())
                .reason(request.reason())
                .build());

        AdminTrustScoreInterventionRuleItem afterState = AdminTrustScoreInterventionRuleItem.from(rule);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.TRUST_SCORE_INTERVENTION_RULE_CREATED,
                AdminAuditTargetType.TRUST_SCORE_INTERVENTION_RULE,
                rule.getId(),
                request.reason(),
                null,
                afterState
        );

        return afterState;
    }

    /**
     * 점수·기간 조건을 검증하고 존재하는 규칙의 이름이 다른 규칙과 중복되지 않을 때 내용을 교체.
     * 활성 여부는 유지하며 변경 전후 감사 기록을 함께 저장. 규칙 없음·이름 중복·잘못된 조건은 오류.
     */
    @Transactional
    public AdminTrustScoreInterventionRuleItem updateRule(
            Long ruleId,
            AdminTrustScoreInterventionRuleRequest request,
            Long adminUserId
    ) {
        validateRuleRequest(request);
        TrustScoreInterventionRule rule = getRule(ruleId);
        if (trustScoreInterventionRuleRepository.existsByRuleNameAndIdNot(request.ruleName(), ruleId)) {
            throw new AdminException(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_DUPLICATED);
        }

        AdminTrustScoreInterventionRuleItem beforeState = AdminTrustScoreInterventionRuleItem.from(rule);
        rule.update(
                request.ruleName(),
                request.triggerType(),
                request.actionType(),
                request.minTrustScore(),
                request.maxTrustScore(),
                request.minSubmittedCount(),
                request.minFalseReportCount(),
                request.durationDays(),
                request.priority(),
                request.reason()
        );

        AdminTrustScoreInterventionRuleItem afterState = AdminTrustScoreInterventionRuleItem.from(rule);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.TRUST_SCORE_INTERVENTION_RULE_UPDATED,
                AdminAuditTargetType.TRUST_SCORE_INTERVENTION_RULE,
                rule.getId(),
                request.reason(),
                beforeState,
                afterState
        );

        return afterState;
    }

    @Transactional
    public AdminTrustScoreInterventionRuleToggleResponse enableRule(Long ruleId, Long adminUserId) {
        TrustScoreInterventionRule rule = getRule(ruleId);
        boolean beforeEnabled = rule.isEnabled();
        rule.enable();
        recordRuleToggleAudit(rule, beforeEnabled, adminUserId, AdminAuditAction.TRUST_SCORE_INTERVENTION_RULE_ENABLED);
        return new AdminTrustScoreInterventionRuleToggleResponse(rule.getId(), true, "Trust Score 개입 규칙을 활성화했습니다.");
    }

    @Transactional
    public AdminTrustScoreInterventionRuleToggleResponse disableRule(Long ruleId, Long adminUserId) {
        TrustScoreInterventionRule rule = getRule(ruleId);
        boolean beforeEnabled = rule.isEnabled();
        rule.disable();
        recordRuleToggleAudit(rule, beforeEnabled, adminUserId, AdminAuditAction.TRUST_SCORE_INTERVENTION_RULE_DISABLED);
        return new AdminTrustScoreInterventionRuleToggleResponse(rule.getId(), false, "Trust Score 개입 규칙을 비활성화했습니다.");
    }

    /**
     * 활성 규칙을 priority·ID 오름차순으로 검사해 첫 일치 규칙 하나를 적용.
     * TEMPORARY_RESTRICT만 실제 제한 만료를 변경하고 다른 조치는 평가 결과와 감사 기록만 남김.
     * 반복 평가 시 임시 제한 기준은 기존 만료가 아닌 현재 시각.
     */
    @Transactional
    public AdminTrustScoreInterventionEvaluationResponse evaluateReporter(Long reporterUserId, Long adminUserId) {
        ReporterModerationPolicy policy = reporterModerationPolicyRepository.findById(reporterUserId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TRUST_SCORE_REPORTER_POLICY_NOT_FOUND));

        return trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .filter(rule -> rule.matches(policy))
                .findFirst()
                .map(rule -> applyRule(policy, rule, adminUserId))
                .orElseGet(() -> new AdminTrustScoreInterventionEvaluationResponse(
                        policy.getReporterUserId(),
                        policy.getTrustScore(),
                        null,
                        null,
                        null,
                        policy.getRestrictedUntil(),
                        NO_MATCHED_RULE_MESSAGE
                ));
    }

    private TrustScoreInterventionRule getRule(Long ruleId) {
        return trustScoreInterventionRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_NOT_FOUND));
    }

    private void validateRuleRequest(AdminTrustScoreInterventionRuleRequest request) {
        if (request.minTrustScore() > request.maxTrustScore()) {
            throw new AdminException(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_INVALID_REQUEST);
        }
        if (request.actionType() == TrustScoreInterventionAction.TEMPORARY_RESTRICT) {
            if (request.durationDays() == null || request.durationDays() < 1 || request.durationDays() > 365) {
                throw new AdminException(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_INVALID_REQUEST);
            }
            return;
        }
        if (request.durationDays() != null) {
            throw new AdminException(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_INVALID_REQUEST);
        }
    }

    private void recordRuleToggleAudit(
            TrustScoreInterventionRule rule,
            boolean beforeEnabled,
            Long adminUserId,
            AdminAuditAction action
    ) {
        adminAuditLogService.record(
                adminUserId,
                action,
                AdminAuditTargetType.TRUST_SCORE_INTERVENTION_RULE,
                rule.getId(),
                rule.getReason(),
                Map.of("enabled", beforeEnabled),
                Map.of("enabled", rule.isEnabled())
        );
    }

    private AdminTrustScoreInterventionEvaluationResponse applyRule(
            ReporterModerationPolicy policy,
            TrustScoreInterventionRule rule,
            Long adminUserId
    ) {
        LocalDateTime restrictedUntil = policy.getRestrictedUntil();
        LocalDateTime beforeRestrictedUntil = restrictedUntil;
        if (rule.getActionType() == TrustScoreInterventionAction.TEMPORARY_RESTRICT && rule.getDurationDays() != null) {
            restrictedUntil = LocalDateTime.now(clock).plusDays(rule.getDurationDays());
            policy.restrictUntil(restrictedUntil, rule.getReason());
        }

        Map<String, Object> beforeState = new HashMap<>();
        beforeState.put("reporterUserId", policy.getReporterUserId());
        beforeState.put("restrictedUntil", beforeRestrictedUntil);
        Map<String, Object> afterState = new HashMap<>();
        afterState.put("reporterUserId", policy.getReporterUserId());
        afterState.put("trustScore", policy.getTrustScore());
        afterState.put("matchedRuleId", rule.getId());
        afterState.put("actionType", rule.getActionType());
        afterState.put("restrictedUntil", restrictedUntil);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.TRUST_SCORE_INTERVENTION_EVALUATED,
                AdminAuditTargetType.TRUST_SCORE_INTERVENTION_RULE,
                rule.getId(),
                rule.getReason(),
                beforeState,
                afterState
        );

        return new AdminTrustScoreInterventionEvaluationResponse(
                policy.getReporterUserId(),
                policy.getTrustScore(),
                rule.getId(),
                rule.getRuleName(),
                rule.getActionType(),
                restrictedUntil,
                EVALUATED_MESSAGE
        );
    }
}
