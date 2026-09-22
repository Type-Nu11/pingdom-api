package com.typenull.pingdom.moderation.application.query.outbox;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventItem;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OUTBOX_RECOVERY 권한을 검사하고 payload를 제외한 처리 상태·시도 횟수·오류를 조회합니다.
 * 조회 기간이 역전되면 거절하고 공백 집계 식별자 필터는 생략합니다. 이벤트 처리 자체는 실행하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminOutboxEventQueryServiceImpl implements AdminOutboxEventQueryService {

    private final AdminRoleAuthorizationService authorizationService;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * OUTBOX_RECOVERY 권한을 확인하고 상태·이벤트 유형·집계 식별자·생성 기간에 맞는 이벤트의 운영 정보를 반환합니다.
     * 역전된 기간은 거절하고 page는 1 이상·limit는 1~100으로 보정하며 공백 집계 필터는 적용하지 않습니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminOutboxEventResponse list(
            Long adminUserId,
            OutboxEventStatus status,
            OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.OUTBOX_RECOVERY);
        validatePeriod(from, to);

        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("eventId"))
        );

        Page<OutboxEvent> eventPage = outboxEventRepository.findByFilters(
                status,
                eventType,
                normalize(aggregateType),
                normalize(aggregateId),
                from != null,
                from,
                to != null,
                to,
                pageable
        );
        List<AdminOutboxEventItem> events = eventPage.getContent().stream()
                .map(AdminOutboxEventItem::from)
                .toList();

        return AdminOutboxEventResponse.of(
                events,
                safePage,
                safeLimit,
                eventPage.getTotalElements(),
                eventPage.getTotalPages()
        );
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdminException(AdminErrorCode.INVALID_OUTBOX_EVENT_FILTER_PERIOD);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
