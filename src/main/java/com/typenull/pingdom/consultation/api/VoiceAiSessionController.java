package com.typenull.pingdom.consultation.api;

import com.typenull.pingdom.consultation.api.dto.VoiceAiEnvelopeResponse;
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
 * 음성 입력을 외부 provider에 직접 노출하지 않는 인증 Gateway입니다.
 * 현재 전송 방식은 단일 최종 JSON 응답(request-response)이며 stream chunk는 계약에 포함하지 않습니다.
 */
@RestController
@RequestMapping("/voice-ai/sessions")
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
    @Operation(summary = "음성 AI 메시지 전송", description = "ProviderEnvelope v1 최종 JSON만 반환합니다. requestId는 앱 재전송 시 동일하게 유지해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최종 envelope 반환"),
            @ApiResponse(responseCode = "410", description = "만료 또는 종료된 세션", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "provider 장애 또는 계약 위반", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))
    })
    public VoiceAiEnvelopeResponse send(
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
