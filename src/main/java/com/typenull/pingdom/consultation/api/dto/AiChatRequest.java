package com.typenull.pingdom.consultation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "앱이 대화 이력과 실행 가능한 function 도구를 함께 전달하는 AI chat 요청")
public record AiChatRequest(
        @NotEmpty @Size(max = 50) @Valid
        @ArraySchema(schema = @Schema(implementation = Message.class))
        List<Message> messages,
        @Size(max = 20) @Valid
        @ArraySchema(schema = @Schema(implementation = Tool.class))
        List<Tool> tools
) {
    public AiChatRequest {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    @Schema(description = "user·assistant·tool 대화 메시지. tool은 이전 assistant tool_calls의 ID를 참조합니다.")
    public record Message(
            @NotBlank @Schema(allowableValues = {"user", "assistant", "tool"}) String role,
            @Schema(description = "user/assistant 텍스트 또는 앱이 실행한 tool 결과") JsonNode content,
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls
    ) {
        public Message {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    @Schema(description = "앱이 등록한 function 도구")
    public record Tool(
            @NotBlank @Schema(allowableValues = "function") String type,
            @Valid Function function
    ) {
    }

    public record Function(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 500) String description,
            @Schema(description = "JSON Schema object") JsonNode parameters
    ) {
    }

    public record ToolCall(
            @NotBlank String id,
            @NotBlank @Schema(allowableValues = "function") String type,
            @Valid FunctionCall function
    ) {
    }

    public record FunctionCall(
            @NotBlank String name,
            @Schema(description = "function 인자 JSON object") JsonNode arguments
    ) {
    }
}
