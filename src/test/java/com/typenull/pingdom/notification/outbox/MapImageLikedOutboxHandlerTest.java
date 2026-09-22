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

    /**
     * 좋아요 알림 발송 결과에 재시도 가능한 실패가 있으면 이벤트 ID를 포함한 RetryableFcmDeliveryException으로 Outbox 처리자에게 전달하는지 검증한다.
     */
    @Test
    void propagatesRetryableLikeDeliveryFailure() {
        MapImageLikedOutboxHandler handler = new MapImageLikedOutboxHandler(fcmService, new ObjectMapper());
        when(fcmService.sendLikeNotification(1L, 2L, "event-id"))
                .thenReturn(new FcmDispatchResult(null, true));

        assertThatThrownBy(() -> handler.handle("event-id", "{\"ownerId\":1,\"likerId\":2}"))
                .isInstanceOf(RetryableFcmDeliveryException.class)
                .hasMessageContaining("event-id");
    }
}
