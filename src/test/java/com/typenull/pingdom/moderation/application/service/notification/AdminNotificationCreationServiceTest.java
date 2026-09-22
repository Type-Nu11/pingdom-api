package com.typenull.pingdom.moderation.application.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminNotificationCreationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AdminNotificationRecipientResolver recipientResolver;
    @Mock
    private NotificationsRepository notificationsRepository;

    private AdminNotificationCreationService service;

    /**
     * 알림 본문과 생성 시각을 비교할 수 있도록 고정 Clock으로 관리자 알림 생성 서비스를 구성.
     */
    @BeforeEach
    void setUp() {
        service = new AdminNotificationCreationService(recipientResolver, notificationsRepository, CLOCK);
    }

    /**
     * 수신 대상 관리자 두 명에게 동일한 신고 접수 본문·이벤트 키·현재 시각으로 중복 방지 삽입을 호출하고 생성 수 2를 반환하는지 검증.
     */
    @Test
    void createsNotificationsForEligibleAdmins() {
        when(recipientResolver.resolve(NotificationType.ADMIN_REPORT_RECEIVED)).thenReturn(List.of(1L, 2L));
        when(notificationsRepository.insertAdminNotificationIfAbsent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(1);

        int createdCount = service.create(
                NotificationType.ADMIN_REPORT_RECEIVED,
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                "report:30",
                List.of("30", "12")
        );

        assertThat(createdCount).isEqualTo(2);
        verify(notificationsRepository).insertAdminNotificationIfAbsent(
                1L,
                NotificationType.ADMIN_REPORT_RECEIVED.name(),
                "신고 접수 알림",
                "신고 ID 30 접수가 게시글 ID 12에 등록되었습니다.",
                "report:30",
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                LocalDateTime.now(CLOCK)
        );
        verify(notificationsRepository).insertAdminNotificationIfAbsent(
                2L,
                NotificationType.ADMIN_REPORT_RECEIVED.name(),
                "신고 접수 알림",
                "신고 ID 30 접수가 게시글 ID 12에 등록되었습니다.",
                "report:30",
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                LocalDateTime.now(CLOCK)
        );
    }

    /**
     * 일반 사용자용 좋아요 유형을 관리자 알림으로 생성하려 하면 IllegalArgumentException으로 거절하는지 검증.
     */
    @Test
    void rejectsUserNotificationType() {
        assertThatThrownBy(() -> service.create(
                NotificationType.NEW_LIKE,
                "NEW_LIKE:1",
                "post:1",
                List.of("liker")
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
