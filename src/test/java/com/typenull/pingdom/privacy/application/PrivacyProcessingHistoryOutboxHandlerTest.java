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

    /**
     * 실제 Jackson 역직렬화기와 감사 이력 저장소 mock을 연결해 Outbox payload 처리를 검증.
     */
    @BeforeEach
    void setUp() {
        handler = new PrivacyProcessingHistoryOutboxHandler(privacyProcessingHistoryRepository, objectMapper);
    }

    /**
     * 처리되지 않은 개인정보 이벤트를 역직렬화하면 이벤트 ID·대상 사용자·EXPORT_REQUESTED·발생 시각을 이력에 저장하는지 검증.
     */
    @Test
    void storesPrivacyOutboxHistory() throws Exception {
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

    /**
     * 같은 이벤트 ID와 대상 사용자 이력이 이미 있으면 재처리 시 저장하지 않는지 검증.
     */
    @Test
    void skipsDuplicatePrivacyHistory() throws Exception {
        when(privacyProcessingHistoryRepository.existsByOutboxEventIdAndSubjectUserId(EVENT_ID, 10L)).thenReturn(true);

        handler.handle(EVENT_ID, objectMapper.writeValueAsString(payload()));

        verify(privacyProcessingHistoryRepository, never()).save(any());
    }

    /**
     * 이력 저장의 임시 DB 오류가 같은 예외 타입·메시지로 전파되는지 확인해 Outbox 재시도 판단에 실패가 전달되도록 함.
     */
    @Test
    void propagatesPrivacyHistoryFailure() throws Exception {
        when(privacyProcessingHistoryRepository.existsByOutboxEventIdAndSubjectUserId(EVENT_ID, 10L)).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary database failure"))
                .when(privacyProcessingHistoryRepository)
                .save(any(PrivacyProcessingHistory.class));

        assertThatThrownBy(() -> handler.handle(EVENT_ID, objectMapper.writeValueAsString(payload())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary database failure");
    }

    /**
     * 빈 JSON payload는 필수 값 누락을 설명하는 IllegalArgumentException으로 거절되는지 검증.
     */
    @Test
    void rejectsMissingPrivacyPayloadFields() {
        assertThatThrownBy(() -> handler.handle(EVENT_ID, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("개인정보 처리 이력 Outbox payload에 필수 값이 없습니다.");
    }

    /**
     * 사용자 10의 데이터 export 요청과 요청 ID·고정 발생 시각을 담은 유효한 개인정보 처리 payload를 제공.
     */
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
