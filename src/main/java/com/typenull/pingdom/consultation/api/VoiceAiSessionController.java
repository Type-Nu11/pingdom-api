package com.typenull.pingdom.consultation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.api.dto.VoiceAiMessageRequest;
import com.typenull.pingdom.consultation.api.dto.VoiceAiSessionResponse;
import com.typenull.pingdom.consultation.application.VoiceAiSessionService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 음성 입력의 외부 provider 직접 노출을 차단하는 인증 Gateway.
 * 현재 전송 계약은 단일 최종 JSON 응답(request-response)으로 한정하며 stream chunk는 제외.
 */
@RestController
@RequestMapping(value = "/voice-ai/sessions", produces = "application/json")
@Tag(name = "Voice AI", description = "인증 사용자 음성 AI Gateway API")
public class VoiceAiSessionController {
    private final VoiceAiSessionService voiceAiSessionService;

    public VoiceAiSessionController(VoiceAiSessionService voiceAiSessionService) {
        this.voiceAiSessionService = voiceAiSessionService;
    }

    @PostMapping
    @Operation(summary = "음성 AI 세션 생성", description = "JWT 사용자 소유 세션을 생성합니다. 세션은 5분 뒤 만료됩니다.")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "세션 생성 성공"))
    public ResponseEntity<VoiceAiSessionResponse> create(@CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voiceAiSessionService.create(user.userId()));
    }

    @PostMapping("/{sessionId}/messages")
    @RateLimited(RateLimitAction.CONSULTATION_INTRO)
    @Operation(summary = "음성 AI 메시지 전송", description = """
            ProviderEnvelope v1 최종 JSON 1개만 반환합니다. SSE/WebSocket/reconnect cursor는 지원하지 않습니다.
            최종 envelope는 UTF-8 16 KiB 이하이며 id는 requestId와 정확히 일치합니다.
            활성 세션 내 requestId를 대소문자 구분하여 비교합니다. 동일 ID·동일 text(UTF-8 SHA-256)는 저장된 결과를
            반환하고 다른 text는 409 REPLAY_CONFLICT입니다. 세션 단위로 전송·갱신·종료를 직렬화하므로 진행 중 재요청은
            선행 트랜잭션 완료 후 재검사합니다. 실패하여 결과가 저장되지 않았다면 재시도에서 provider를 다시 호출합니다.
            결과는 세션이 활성인 동안 재사용하며 refresh는 이 기간을 연장합니다. 만료·종료 후에는 replay도 410입니다.
            현재 저장 데이터의 자동 삭제 기간은 설정되어 있지 않습니다. 앱의 epoch/generation 및 256개 ledger는 앱 소유입니다.
            앱의 30초 deadline이나 연결 종료는 서버/provider 취소를 보장하지 않습니다. 서버 처리가 커밋되었다면
            동일 ID/text 재시도로 결과를 회수할 수 있습니다. DELETE는 진행 중 전송 완료 후 세션을 종료하며 호출을 취소하지 않습니다.
            provider 연결/read timeout은 gemini 설정(기본 2초/5초)이며 서버 전체 deadline을 의미하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최종 envelope 반환"),
            @ApiResponse(responseCode = "410", description = "만료 또는 종료된 세션", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "provider 장애 또는 계약 위반", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))
    })
    public JsonNode send(
            @PathVariable String sessionId,
            @Valid @RequestBody VoiceAiMessageRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return voiceAiSessionService.send(sessionId, user.userId(), request.text(), request.requestId());
    }

    @PostMapping("/{sessionId}/refresh")
    @Operation(summary = "음성 AI 세션 갱신", description = "활성 세션의 만료 시각을 현재 시점에서 5분으로 갱신합니다.")
    public VoiceAiSessionResponse refresh(@PathVariable String sessionId, @CurrentUser JwtAuthenticatedUser user) {
        return voiceAiSessionService.refresh(sessionId, user.userId());
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "음성 AI 세션 종료", description = "동일 세션의 반복 종료 요청은 성공으로 처리됩니다.")
    public ResponseEntity<Void> close(@PathVariable String sessionId, @CurrentUser JwtAuthenticatedUser user) {
        voiceAiSessionService.close(sessionId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
