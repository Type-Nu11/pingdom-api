package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.VisitEvidenceResponse;
import com.typenull.pingdom.verification.application.VisitEvidenceService;
import com.typenull.pingdom.verification.application.VisitEvidenceService.VisitEvidenceDownload;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/location-check-ins/{checkInId}/evidence")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class VisitEvidenceController {
    private final VisitEvidenceService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "방문 체크인 증빙 업로드",
            description = "본인의 완료된 체크인에 JPEG 또는 PNG 증빙 이미지 한 개를 등록합니다. 증빙은 보관 기간 만료 후 삭제됩니다.")
    @ApiResponse(responseCode = "201", description = "증빙 등록 완료")
    @ApiResponse(responseCode = "400", description = "비어 있거나 지원하지 않는 이미지",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 소유 체크인을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "체크인 증빙이 이미 존재함",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "파일 크기 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "증빙 저장소 사용 불가",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<VisitEvidenceResponse> upload(@PathVariable Long checkInId,
            @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(user.userId(), checkInId, file));
    }

    @GetMapping
    @Operation(summary = "내 방문 체크인 증빙 조회")
    @ApiResponse(responseCode = "404", description = "본인 소유 체크인 또는 증빙을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public VisitEvidenceResponse get(@PathVariable Long checkInId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.get(user.userId(), checkInId);
    }

    @GetMapping("/file")
    @Operation(summary = "내 방문 체크인 증빙 파일 다운로드")
    @ApiResponse(responseCode = "200", description = "증빙 이미지")
    @ApiResponse(responseCode = "404", description = "본인 소유 체크인 또는 증빙을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<byte[]> download(@PathVariable Long checkInId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        VisitEvidenceDownload download = service.download(user.userId(), checkInId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }
}
