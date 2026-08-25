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

    @Test
    void requestedHandlerDeliversNotificationToOwner() throws Exception {
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

    @Test
    void reminderHandlerDeliversNotificationToOwner() throws Exception {
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

    private PlaceInformationReverificationOutboxPayload payload() {
        return new PlaceInformationReverificationOutboxPayload(
                1L, 10L, "테스트 장소", 20L, PlaceInformationReverificationStatus.REQUESTED,
                0, LocalDateTime.of(2026, 7, 21, 12, 0));
    }
}
