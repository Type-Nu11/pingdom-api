package com.typenull.pingdom.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.notification.application.service.FcmDispatchResult;
import com.typenull.pingdom.notification.application.service.FcmService;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationStatus;
import com.typenull.pingdom.place.outbox.information.PlaceInformationReverificationOutboxPayload;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceInformationReverificationOutboxHandlerTest {
    @Mock FcmService fcmService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 재확인 요청 핸들러가 지원 이벤트 유형을 선언하고 페이로드의 점주·장소명·이벤트 ID로 요청 알림을 위임하는지 검증한다.
     */
    @Test
    void deliversReverificationRequestNotification() throws Exception {
        var handler = new PlaceInformationReverificationRequestedOutboxHandler(fcmService, objectMapper);
        String payload = objectMapper.writeValueAsString(payload());
        when(fcmService.sendPlaceInformationReverificationNotification(
                20L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED, "테스트 장소", "event-1"))
                .thenReturn(new FcmDispatchResult(null, false));

        handler.handle("event-1", payload);

        assertThat(handler.supportedType()).isEqualTo(OutboxEventType.PLACE_INFORMATION_REVERIFICATION_REQUESTED);
        verify(fcmService).sendPlaceInformationReverificationNotification(
                eq(20L), eq(NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED),
                eq("테스트 장소"), eq("event-1"));
    }

    /**
     * 재확인 리마인더 핸들러의 지원 이벤트 유형과 점주·장소명·이벤트 ID를 사용한 리마인더 발송 위임을 검증한다.
     */
    @Test
    void deliversReverificationReminderNotification() throws Exception {
        var handler = new PlaceInformationReverificationReminderOutboxHandler(fcmService, objectMapper);
        when(fcmService.sendPlaceInformationReverificationNotification(
                20L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER, "테스트 장소", "event-2"))
                .thenReturn(new FcmDispatchResult(null, false));

        handler.handle("event-2", objectMapper.writeValueAsString(payload()));

        assertThat(handler.supportedType()).isEqualTo(OutboxEventType.PLACE_INFORMATION_REVERIFICATION_REMINDER_REQUESTED);
        verify(fcmService).sendPlaceInformationReverificationNotification(
                eq(20L), eq(NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER),
                eq("테스트 장소"), eq("event-2"));
    }

    /**
     * 요청 및 리마인더 역직렬화에 사용할 재확인 식별자·장소·점주·요청 상태·기한을 제공한다.
     */
    private PlaceInformationReverificationOutboxPayload payload() {
        return new PlaceInformationReverificationOutboxPayload(
                1L, 10L, "테스트 장소", 20L, PlaceInformationReverificationStatus.REQUESTED,
                0, LocalDateTime.of(2026, 7, 21, 12, 0));
    }
}
