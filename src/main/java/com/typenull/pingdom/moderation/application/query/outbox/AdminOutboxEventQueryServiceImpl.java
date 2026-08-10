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

@Service
@RequiredArgsConstructor
public class AdminOutboxEventQueryServiceImpl implements AdminOutboxEventQueryService {

    private final AdminRoleAuthorizationService authorizationService;
    private final OutboxEventRepository outboxEventRepository;

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
                from,
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
