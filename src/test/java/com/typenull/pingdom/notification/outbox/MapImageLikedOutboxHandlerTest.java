package com.typenull.pingdom.notification.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.notification.application.service.FcmDispatchResult;
import com.typenull.pingdom.notification.application.service.FcmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapImageLikedOutboxHandlerTest {

    @Mock
    private FcmService fcmService;

    @Test
    void retryableFcmFailureIsPropagatedToOutboxProcessor() {
        MapImageLikedOutboxHandler handler = new MapImageLikedOutboxHandler(fcmService, new ObjectMapper());
        when(fcmService.sendLikeNotification(1L, 2L, "event-id"))
                .thenReturn(new FcmDispatchResult(null, true));

        assertThatThrownBy(() -> handler.handle("event-id", "{\"ownerId\":1,\"likerId\":2}"))
                .isInstanceOf(RetryableFcmDeliveryException.class)
                .hasMessageContaining("event-id");
    }
}
