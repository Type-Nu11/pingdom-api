package com.typenull.pingdom.consultation.api;

import com.typenull.pingdom.consultation.api.dto.AiChatRequest;
import com.typenull.pingdom.consultation.api.dto.AiChatResponse;
import com.typenull.pingdom.consultation.application.AiChatService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/ai", produces = "application/json")
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = "AI 대화", description = "인증 앱이 도구를 등록하고 실행 결과를 후속 대화로 전달하는 AI API")
public class AiChatController {
    private final AiChatService service;

    public AiChatController(AiChatService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    @RateLimited(RateLimitAction.CONSULTATION_INTRO)
    @Operation(summary = "앱 도구 기반 AI 대화", description = """
            messages 전체와 앱이 등록한 tools를 모델에 전달합니다. assistant 응답은 content와 tool_calls를 동시에 포함할 수 있습니다.
            앱은 tool_calls를 실행한 뒤 같은 호출 ID를 tool_call_id에 넣은 tool 메시지와 이전 대화 전체를 재전송합니다.
            기존 /voice-ai/sessions API와 ProviderEnvelope v1 계약은 그대로 유지되며 이 endpoint와 상태를 공유하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일반 답변 또는 tool 호출 반환",
                    content = @Content(schema = @Schema(implementation = AiChatResponse.class), examples = @ExampleObject(value = """
                            {"content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"searchNearbyPlaces","arguments":{"keyword":"갈비탕","limit":3}}}]}
                            """))),
            @ApiResponse(responseCode = "400", description = "미등록 도구, 잘못된 호출 ID 또는 schema 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "provider 장애 또는 응답 계약 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AiChatResponse chat(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "일반 장소 검색", value = """
                            {"messages":[{"role":"user","content":"갈비탕집 있어?"}],"tools":[{"type":"function","function":{"name":"searchNearbyPlaces","description":"현재 위치 주변 장소를 조회합니다.","parameters":{"type":"object","properties":{"keyword":{"type":"string","minLength":1,"maxLength":100},"limit":{"type":"integer","minimum":1,"maximum":3}},"required":[],"additionalProperties":false}}}]}
                            """),
                    @ExampleObject(name = "도구 결과 후속 대화", value = """
                            {"messages":[{"role":"user","content":"갈비탕집 있어?"},{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"searchNearbyPlaces","arguments":{"keyword":"갈비탕","limit":3}}}]},{"role":"tool","tool_call_id":"call_1","content":{"places":[]}}],"tools":[{"type":"function","function":{"name":"searchNearbyPlaces","description":"현재 위치 주변 장소를 조회합니다.","parameters":{"type":"object","properties":{"keyword":{"type":"string"},"limit":{"type":"integer","minimum":1,"maximum":3}},"required":[],"additionalProperties":false}}}]}
                            """)
            }))
            @Valid @RequestBody AiChatRequest request) {
        return service.chat(request);
    }
}
