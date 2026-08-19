package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.conversion.MapLinkConversionRequest;
import com.typenull.pingdom.place.application.service.conversion.MapLinkConversionEventService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places/{placeId}/map-link-conversions")
@RequiredArgsConstructor
@Validated
@Tag(name = "App Place", description = "앱용 장소 API")
public class MapLinkConversionController {
    private final MapLinkConversionEventService service;

    @PostMapping
    @Operation(
            summary = "지도 링크 전환 기록",
            description = "같은 사용자·장소·전환 유형에서 requestId가 같은 재시도는 중복 집계하지 않고 204를 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "전환 기록 또는 동일 요청 재처리 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "전환 요청값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> record(
            @Parameter(description = "장소 ID", example = "1")
            @Min(value = 1, message = "placeId는 1 이상이어야 합니다.")
            @PathVariable long placeId,
            @Valid @RequestBody MapLinkConversionRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        service.record(
                user.userId(),
                placeId,
                request.linkType(),
                request.provider(),
                request.requestId(),
                LocalDateTime.now()
        );
        return ResponseEntity.noContent().build();
    }
}
