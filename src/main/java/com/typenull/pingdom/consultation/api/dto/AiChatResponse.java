package com.typenull.pingdom.consultation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "일반 답변과 앱이 실행할 tool 호출. 둘은 한 응답에 함께 포함될 수 있습니다.")
public record AiChatResponse(
        String content,
        @JsonProperty("tool_calls") List<AiChatRequest.ToolCall> toolCalls
) {
    public AiChatResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
