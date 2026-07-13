package com.typenull.pingdom.moderation.application.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void record(
            Long actorUserId,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            Object targetId,
            String reason,
            Object beforeState,
            Object afterState
    ) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");

        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .actorUsername(resolveActorUsername(actorUserId))
                .action(action)
                .targetType(targetType)
                .targetId(String.valueOf(targetId))
                .reason(reason)
                .beforeState(serializeState(beforeState))
                .afterState(serializeState(afterState))
                .requestId(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY))
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    private String resolveActorUsername(Long actorUserId) {
        if (actorUserId == null) {
            return null;
        }
        return userRepository.findById(actorUserId)
                .map(User::getUsername)
                .orElse(null);
    }

    private String serializeState(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String stringState) {
            return stringState;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.AUDIT_LOG_WRITE_FAILED, exception);
        }
    }
}
