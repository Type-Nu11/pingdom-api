package com.typenull.pingdom.consultation.api;

import com.typenull.pingdom.consultation.api.dto.ConsultationIntroRequest;
import com.typenull.pingdom.consultation.api.dto.ConsultationIntroResponse;
import com.typenull.pingdom.consultation.application.ConsultationIntroService;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consultations")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class ConsultationIntroController {

    private final ConsultationIntroService consultationIntroService;

    public ConsultationIntroController(ConsultationIntroService consultationIntroService) {
        this.consultationIntroService = consultationIntroService;
    }

    @PostMapping("/intro")
    @Operation(
            summary = "첫 상담 안내 생성",
            description = "첫 사용자 질문만 외부 AI에 전달해 짧은 안내문을 생성합니다. AI를 사용할 수 없으면 기본 안내문을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "첫 상담 안내 생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "429", description = "IP 요청 제한 초과")
    })
    @RateLimited(RateLimitAction.CONSULTATION_INTRO)
    public ResponseEntity<ConsultationIntroResponse> createIntro(
            @Valid @RequestBody ConsultationIntroRequest request
    ) {
        return ResponseEntity.ok(consultationIntroService.createIntro(request.message()));
    }
}
