package com.typenull.pingdom.moderation.application.service.place.recommendation;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminPlaceServiceSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficPolicyItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateResponse;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.recommendation.AdminRecommendationPolicyChangeHistory;
import com.typenull.pingdom.moderation.domain.recommendation.AdminRecommendationPolicyChangeType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminRecommendationPolicyChangeHistoryRepository;
import com.typenull.pingdom.place.application.service.recommendation.policy.PlaceRecommendationPolicyService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 추천 트래픽 정책 변경과 추천 스냅샷 재동기화를 담당한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlaceRecommendationPolicyService {
    private final ObjectMapper objectMapper;
    private final PlaceRecommendationPolicyService placeRecommendationPolicyService;
    private final PlaceRecommendationSnapshotResyncService placeRecommendationSnapshotResyncService;
    private final AdminRecommendationPolicyChangeHistoryRepository adminRecommendationPolicyChangeHistoryRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional
    public AdminPlaceRecommendationTrafficUpdateResponse updateRecommendationTraffic(
            Long adminUserId,
            AdminPlaceRecommendationTrafficUpdateRequest request
    ) {
        validateRecommendationTrafficRequest(request);
        List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> beforePolicies =
                placeRecommendationPolicyService.getTrafficPolicies();

        Map<String, Integer> requestedTrafficByVersion = new LinkedHashMap<>();
        Map<String, PlaceRecommendationPolicyService.PolicyUpdateCommand> policyCommands = new LinkedHashMap<>();
        for (AdminPlaceRecommendationTrafficUpdateItem policy : request.policies()) {
            String recommendationVersion = policy.recommendationVersion().trim();
            if (!placeRecommendationPolicyService.supportsVersion(recommendationVersion)) {
                throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_VERSION_NOT_FOUND);
            }
            if (requestedTrafficByVersion.putIfAbsent(recommendationVersion, policy.trafficPercentage()) != null) {
                throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
            }
            boolean enabled = policy.enabled() == null || policy.enabled();
            String fallbackVersion = AdminPlaceServiceSupport.trimToNull(policy.fallbackVersion());
            if (!enabled) {
                if (!StringUtils.hasText(fallbackVersion)) {
                    throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
                }
                if (!placeRecommendationPolicyService.supportsVersion(fallbackVersion)
                        || recommendationVersion.equals(fallbackVersion)) {
                    throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
                }
            } else {
                fallbackVersion = null;
            }
            policyCommands.put(
                    recommendationVersion,
                    new PlaceRecommendationPolicyService.PolicyUpdateCommand(
                            policy.trafficPercentage(),
                            enabled,
                            fallbackVersion
                    )
            );
        }
        if (requestedTrafficByVersion.size() != beforePolicies.size()) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID);
        }
        validateFallbackCycle(policyCommands);

        long enabledPolicyCount = policyCommands.values().stream()
                .filter(PlaceRecommendationPolicyService.PolicyUpdateCommand::enabled)
                .count();
        if (enabledPolicyCount == 0) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
        }

        int totalTrafficPercentage = requestedTrafficByVersion.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (totalTrafficPercentage != 100) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID);
        }

        List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> updatedPolicies =
                placeRecommendationPolicyService.updateTrafficPolicies(policyCommands);
        List<AdminRecommendationPolicyChangeHistory> policyHistories = buildRecommendationPolicyHistories(
                adminUserId,
                request.reason().trim(),
                beforePolicies,
                updatedPolicies
        );
        if (!policyHistories.isEmpty()) {
            adminRecommendationPolicyChangeHistoryRepository.saveAll(policyHistories);
        }

        adminAuditLogService.record(
                adminUserId,
                updatedPolicies.stream().anyMatch(policy -> !policy.enabled())
                        ? AdminAuditAction.PLACE_RECOMMENDATION_KILL_SWITCH_UPDATED
                        : AdminAuditAction.PLACE_RECOMMENDATION_TRAFFIC_UPDATED,
                AdminAuditTargetType.PLACE,
                placeRecommendationPolicyService.getDefaultVersion(),
                request.reason().trim(),
                toTrafficAuditState(beforePolicies),
                toTrafficAuditState(updatedPolicies)
        );

        log.info(
                "Admin updated recommendation traffic. adminUserId={}, policies={}",
                adminUserId,
                requestedTrafficByVersion
        );

        return new AdminPlaceRecommendationTrafficUpdateResponse(
                placeRecommendationPolicyService.getDefaultVersion(),
                updatedPolicies.stream()
                        .map(policy -> new AdminPlaceRecommendationTrafficPolicyItem(
                                policy.version(),
                                policy.stage().name(),
                                policy.trafficPercentage(),
                                policy.enabled(),
                                policy.fallbackVersion()
                        ))
                        .toList(),
                "추천 버전 트래픽 비율을 수정했습니다."
        );
    }

    @Transactional
    public PlaceRecommendationSnapshotResyncService.SnapshotResyncResult resyncRecommendationSnapshots() {
        return placeRecommendationSnapshotResyncService.resyncAll();
    }

    private void validateRecommendationTrafficRequest(AdminPlaceRecommendationTrafficUpdateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.reason())
                || request.policies() == null
                || request.policies().isEmpty()) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
        }
        if (request.policies().stream().anyMatch(policy ->
                policy == null
                        || !StringUtils.hasText(policy.recommendationVersion())
                        || policy.trafficPercentage() == null
        )) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
        }
    }

    private void validateFallbackCycle(Map<String, PlaceRecommendationPolicyService.PolicyUpdateCommand> policyCommands) {
        for (String version : policyCommands.keySet()) {
            Set<String> visitedVersions = new HashSet<>();
            String currentVersion = version;
            while (currentVersion != null) {
                if (!visitedVersions.add(currentVersion)) {
                    throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
                }
                PlaceRecommendationPolicyService.PolicyUpdateCommand command = policyCommands.get(currentVersion);
                if (command == null || command.enabled()) {
                    break;
                }
                currentVersion = command.fallbackVersion();
            }
        }
    }

    private List<AdminRecommendationPolicyChangeHistory> buildRecommendationPolicyHistories(
            Long adminUserId,
            String reason,
            List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> beforePolicies,
            List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> afterPolicies
    ) {
        Map<String, PlaceRecommendationPolicyService.RecommendationTrafficPolicy> beforePolicyMap = beforePolicies.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PlaceRecommendationPolicyService.RecommendationTrafficPolicy::version,
                        policy -> policy
                ));
        List<AdminRecommendationPolicyChangeHistory> histories = new ArrayList<>();
        LocalDateTime changedAt = LocalDateTime.now(clock);

        for (PlaceRecommendationPolicyService.RecommendationTrafficPolicy afterPolicy : afterPolicies) {
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy beforePolicy =
                    beforePolicyMap.get(afterPolicy.version());
            if (beforePolicy == null || isSameRecommendationPolicy(beforePolicy, afterPolicy)) {
                continue;
            }

            histories.add(AdminRecommendationPolicyChangeHistory.builder()
                    .recommendationVersion(afterPolicy.version())
                    .changeType(AdminRecommendationPolicyChangeType.TRAFFIC_POLICY)
                    .actorUserId(adminUserId)
                    .reason(reason)
                    .beforeState(writeRecommendationPolicyHistoryValue(toRecommendationPolicyHistoryState(beforePolicy)))
                    .afterState(writeRecommendationPolicyHistoryValue(toRecommendationPolicyHistoryState(afterPolicy)))
                    .changedAt(changedAt)
                    .build());
        }
        return histories;
    }

    private boolean isSameRecommendationPolicy(
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy beforePolicy,
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy afterPolicy
    ) {
        return beforePolicy.trafficPercentage() == afterPolicy.trafficPercentage()
                && beforePolicy.enabled() == afterPolicy.enabled()
                && java.util.Objects.equals(beforePolicy.fallbackVersion(), afterPolicy.fallbackVersion());
    }

    private Map<String, Object> toRecommendationPolicyHistoryState(
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy policy
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("recommendationVersion", policy.version());
        state.put("stage", policy.stage().name());
        state.put("trafficPercentage", policy.trafficPercentage());
        state.put("enabled", policy.enabled());
        state.put("fallbackVersion", policy.fallbackVersion());
        return state;
    }

    private Map<String, Object> toTrafficAuditState(
            List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> policies
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("defaultVersion", placeRecommendationPolicyService.getDefaultVersion());
        state.put("policies", policies.stream()
                .map(policy -> {
                    Map<String, Object> policyState = new LinkedHashMap<>();
                    policyState.put("recommendationVersion", policy.version());
                    policyState.put("stage", policy.stage().name());
                    policyState.put("trafficPercentage", policy.trafficPercentage());
                    policyState.put("enabled", policy.enabled());
                    policyState.put("fallbackVersion", policy.fallbackVersion());
                    return policyState;
                })
                .toList());
        return state;
    }

    private String writeRecommendationPolicyHistoryValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_POLICY_HISTORY_WRITE_FAILED, exception);
        }
    }
}
