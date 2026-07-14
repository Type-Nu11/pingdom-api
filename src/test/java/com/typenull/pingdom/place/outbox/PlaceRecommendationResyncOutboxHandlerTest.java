package com.typenull.pingdom.place.outbox;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        handler = new PlaceRecommendationResyncOutboxHandler(resyncService, objectMapper);
    }

    @Test
    void handlesResyncRequestOutsideOriginalTransaction() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new PlaceRecommendationResyncOutboxPayload(17L, "ADMIN_GEOCODING_UPDATED")
        );

        handler.handle("event-1", payload);

        verify(resyncService).resyncAll();
        assertThat(handler.supportedType()).isEqualTo(OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED);
    }
}
