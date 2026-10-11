package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.consultation.api.dto.AiChatRequest;
import com.typenull.pingdom.consultation.api.dto.AiChatResponse;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiChatServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiChatClient client = mock(GeminiChatClient.class);
    private final AiChatService service = new AiChatService(
            new GeminiProperties(true, "key", null, null, null), client);

    @Test
    void acceptsRegisteredGeneralSearchWithoutReservationArguments() throws Exception {
        AiChatRequest request = request("갈비탕집 있어?");
        AiChatResponse response = new AiChatResponse(null, List.of(new AiChatRequest.ToolCall(
                "call_1", "function", new AiChatRequest.FunctionCall("searchNearbyPlaces",
                mapper.readTree("{\"keyword\":\"갈비탕\",\"limit\":3}")))));
        when(client.chat(request)).thenReturn(response);

        assertThat(service.chat(request)).isEqualTo(response);
    }

    @Test
    void acceptsToolResultLinkedToPreviousAssistantCall() throws Exception {
        AiChatRequest initial = request("갈비탕집 있어?");
        AiChatRequest.ToolCall call = new AiChatRequest.ToolCall("call_1", "function",
                new AiChatRequest.FunctionCall("searchNearbyPlaces", mapper.readTree("{\"keyword\":\"갈비탕\"}")));
        AiChatRequest followUp = new AiChatRequest(List.of(
                initial.messages().getFirst(),
                new AiChatRequest.Message("assistant", null, null, List.of(call)),
                new AiChatRequest.Message("tool", mapper.readTree("{\"places\":[]}"), "call_1", List.of())
        ), initial.tools());
        AiChatResponse answer = new AiChatResponse("주변에서 갈비탕집을 찾지 못했어요.", List.of());
        when(client.chat(followUp)).thenReturn(answer);

        assertThat(service.chat(followUp)).isEqualTo(answer);
    }

    @Test
    void rejectsUnknownToolAndInvalidArgumentsBeforeProviderCall() throws Exception {
        AiChatRequest.ToolCall unknown = new AiChatRequest.ToolCall("call_1", "function",
                new AiChatRequest.FunctionCall("unknown", mapper.createObjectNode()));
        AiChatRequest request = new AiChatRequest(List.of(
                new AiChatRequest.Message("user", mapper.getNodeFactory().textNode("검색"), null, List.of()),
                new AiChatRequest.Message("assistant", null, null, List.of(unknown)),
                new AiChatRequest.Message("tool", mapper.getNodeFactory().textNode("결과"), "call_1", List.of())
        ), request("검색").tools());

        assertThatThrownBy(() -> service.chat(request)).isInstanceOfSatisfying(VoiceAiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(VoiceAiErrorCode.TOOL_CALL_INVALID));
        verifyNoInteractions(client);
    }

    private AiChatRequest request(String text) throws Exception {
        return new AiChatRequest(List.of(new AiChatRequest.Message("user", mapper.getNodeFactory().textNode(text), null, List.of())),
                List.of(new AiChatRequest.Tool("function", new AiChatRequest.Function(
                        "searchNearbyPlaces", "현재 위치 주변 장소 검색", mapper.readTree("""
                        {"type":"object","properties":{"keyword":{"type":"string","minLength":1,"maxLength":100},
                        "limit":{"type":"integer","minimum":1,"maximum":3}},"required":[],"additionalProperties":false}
                        """)))));
    }
}
