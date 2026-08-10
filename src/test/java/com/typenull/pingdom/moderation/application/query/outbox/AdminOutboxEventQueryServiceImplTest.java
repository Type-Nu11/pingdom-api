package com.typenull.pingdom.moderation.application.query.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminOutboxEventQueryServiceImplTest {

    @Mock private AdminRoleAuthorizationService authorizationService;
    @Mock private OutboxEventRepository outboxEventRepository;

    private AdminOutboxEventQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminOutboxEventQueryServiceImpl(authorizationService, outboxEventRepository);
    }

    @Test
    void listsOperationalMetadataWithoutPayload() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 10, 0);
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:10:code",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{\"verificationCode\":\"secret\"}",
                "USER",
                "10",
                now
        );
        when(outboxEventRepository.findByFilters(
                eq(OutboxEventStatus.PENDING),
                eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED),
                eq("USER"),
                eq("10"),
                eq(null),
                eq(null),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(event)));

        AdminOutboxEventResponse response = service.list(
                10L,
                OutboxEventStatus.PENDING,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                " USER ",
                " 10 ",
                null,
                null,
                1,
                20
        );

        verify(authorizationService).requirePermission(10L, AdminPermission.OUTBOX_RECOVERY);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().getFirst().eventId()).isEqualTo(event.getEventId());
        assertThat(response.events().getFirst().aggregateType()).isEqualTo("USER");
    }

    @Test
    void rejectsInvertedPeriodBeforeQueryingRepository() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 11, 0, 0);
        LocalDateTime to = from.minusDays(1);

        assertThatThrownBy(() -> service.list(10L, null, null, null, null, from, to, 1, 20))
                .isInstanceOfSatisfying(
                        AdminException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AdminErrorCode.INVALID_OUTBOX_EVENT_FILTER_PERIOD)
                );
        verify(outboxEventRepository, never()).findByFilters(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }
}
