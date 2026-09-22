package com.typenull.pingdom.place.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationResyncOutboxHandlerTest {

    @Mock
    private PlaceRecommendationSnapshotResyncService resyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlaceRecommendationResyncOutboxHandler handler;

    /** 실제 ObjectMapper와 재동기화 서비스 mock을 연결한 handler를 새로 만든다. */
    @BeforeEach
    void setUp() {
        handler = new PlaceRecommendationResyncOutboxHandler(resyncService, objectMapper);
    }

    /** JSON payload의 장소 ID로 재동기화 서비스를 호출하고 지원 이벤트 유형을 확인한다. 원본 트랜잭션 분리는 이 mock 테스트가 증명하지 않는다. */
    @Test
    void delegatesSnapshotResyncRequest() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new PlaceRecommendationResyncOutboxPayload(17L, "ADMIN_GEOCODING_UPDATED")
        );

        handler.handle("event-1", payload);

        verify(resyncService).resyncPlace(17L);
        assertThat(handler.supportedType()).isEqualTo(OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED);
    }

    /** 장소 ID가 null인 payload는 placeId를 언급하는 입력 오류로 거부하는지 확인한다. */
    @Test
    void rejectsPayloadWithoutPlaceId() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new PlaceRecommendationResyncOutboxPayload(null, "ADMIN_GEOCODING_UPDATED")
        );

        assertThatThrownBy(() -> handler.handle("event-1", payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeId");
    }
}
