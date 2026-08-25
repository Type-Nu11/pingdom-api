package com.typenull.pingdom.privacy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrivacyProcessingHistoryOutboxHandlerTest {

    private static final String EVENT_ID = "event-1";

    @Mock
    private PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PrivacyProcessingHistoryOutboxHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PrivacyProcessingHistoryOutboxHandler(privacyProcessingHistoryRepository, objectMapper);
    }

    @Test
    void Outbox_이벤트를_감사_이력으로_저장한다() throws Exception {
        PrivacyProcessingOutboxPayload payload = payload();
        when(privacyProcessingHistoryRepository.existsByOutboxEventIdAndSubjectUserId(EVENT_ID, 10L)).thenReturn(false);

        handler.handle(EVENT_ID, objectMapper.writeValueAsString(payload));

        ArgumentCaptor<PrivacyProcessingHistory> historyCaptor = ArgumentCaptor.forClass(PrivacyProcessingHistory.class);
        verify(privacyProcessingHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
                .extracting(
                        PrivacyProcessingHistory::getOutboxEventId,
                        PrivacyProcessingHistory::getSubjectUserId,
                        PrivacyProcessingHistory::getAction,
                        PrivacyProcessingHistory::getCreatedAt
                )
                .containsExactly(EVENT_ID, 10L, PrivacyProcessingAction.EXPORT_REQUESTED, payload.occurredAt());
    }

    @Test
    void 동일_Outbox_이벤트가_재처리되면_이력_저장을_건너뛴다() throws Exception {
        when(privacyProcessingHistoryRepository.existsByOutboxEventIdAndSubjectUserId(EVENT_ID, 10L)).thenReturn(true);

        handler.handle(EVENT_ID, objectMapper.writeValueAsString(payload()));

        verify(privacyProcessingHistoryRepository, never()).save(any());
    }

    @Test
    void 저장_실패를_전파해_Outbox_재시도를_유도한다() throws Exception {
        when(privacyProcessingHistoryRepository.existsByOutboxEventIdAndSubjectUserId(EVENT_ID, 10L)).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary database failure"))
                .when(privacyProcessingHistoryRepository)
                .save(any(PrivacyProcessingHistory.class));

        assertThatThrownBy(() -> handler.handle(EVENT_ID, objectMapper.writeValueAsString(payload())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary database failure");
    }

    @Test
    void 필수_값이_없는_payload는_실패로_처리한다() {
        assertThatThrownBy(() -> handler.handle(EVENT_ID, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("개인정보 처리 이력 Outbox payload에 필수 값이 없습니다.");
    }

    private PrivacyProcessingOutboxPayload payload() {
        return new PrivacyProcessingOutboxPayload(
                10L,
                10L,
                com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType.USER,
                PrivacyProcessingAction.EXPORT_REQUESTED,
                "사용자 데이터 export 요청",
                "request-1",
                LocalDateTime.of(2026, 8, 25, 9, 0)
        );
    }
}
