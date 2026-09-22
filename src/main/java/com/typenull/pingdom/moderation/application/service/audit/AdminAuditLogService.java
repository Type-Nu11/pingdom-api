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

/**
 * 업무 변경 전후 상태와 현재 요청 ID·행위자 표시명을 감사 기록으로 저장합니다.
 * 호출 트랜잭션에 참여하므로 직렬화·저장 실패는 업무 변경도 실패시킬 수 있고 별도 독립 감사 트랜잭션은 아닙니다.
 * String 상태는 그대로 저장하고 그 외 객체만 JSON으로 직렬화하며, 민감정보 선별은 호출자가 책임집니다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 필수 action·targetType·targetId를 확인한 뒤 행위자의 현재 이름·MDC 요청 ID와 변경 전후 상태를 저장합니다.
     * 문자열 상태는 그대로, 객체 상태는 JSON으로 보관하며 직렬화 실패를 AUDIT_LOG_WRITE_FAILED로 전달합니다.
     * 호출자의 트랜잭션에 참여하므로 감사 기록 실패는 같은 트랜잭션의 업무 변경도 롤백시킬 수 있습니다.
     */
    @Transactional
    public AdminAuditLog record(
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

        return adminAuditLogRepository.save(AdminAuditLog.builder()
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
