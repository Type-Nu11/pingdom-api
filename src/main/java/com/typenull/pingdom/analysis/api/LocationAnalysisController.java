package com.typenull.pingdom.analysis.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportAccessPolicy;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportGenerationLimiter;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportResponse;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportPageResponse;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportUpdateRequest;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 계정의 이메일 소유권을 확인한 뒤 보고서 생성·보관·조회 흐름을 연결.
 * 생성된 PDF와 HTML이 보관된 후 PDF 응답을 반환하므로, 저장 실패도 생성 요청의 실패로 전달.
 */
@RestController
@RequestMapping("/analysis/reports")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.ANALYSIS)
public class LocationAnalysisController {

    private final LocationAnalysisReportService reportService;
    private final LocationAnalysisReportArchiveService archiveService;
    private final LocationAnalysisReportAccessPolicy accessPolicy;
    private final LocationAnalysisReportGenerationLimiter generationLimiter;

    @PostMapping(value = "/location", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
            summary = "입지 분석 PDF 보고서 생성",
            description = "입력 조건을 AI/MCP 분석 인터페이스로 전달하고 HTML 분석 결과를 PDF로 반환합니다. "
                    + "사용자·IP별 호출 한도는 Redis로 모든 인스턴스에 공유되며, 생성 동시 실행·대기열 상한은 인스턴스별로 적용됩니다. "
                    + "429 응답 시 즉시 반복하지 말고 backoff 후 재시도하세요. Retry-After 헤더는 제공하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF 보고서 생성 성공"),
            @ApiResponse(responseCode = "400", description = "지역 누락 또는 입력값 검증 실패", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "사용자·IP 호출 한도 또는 인스턴스별 생성 동시 실행·대기열 상한 초과", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 분석 응답 처리 실패", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))
    })
    @RateLimited(RateLimitAction.LOCATION_ANALYSIS_REPORT)
    public ResponseEntity<byte[]> generate(
            @Valid @RequestBody LocationAnalysisRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), request.getEmail());
        LocationAnalysisReportService.LocationAnalysisPdf report = generationLimiter.execute(() -> {
            LocationAnalysisReportService.LocationAnalysisPdf generated = reportService.generate(request);
            archiveService.archive(userId(user), request, generated);
            return generated;
        });
        String filename = downloadFilename(report.reportName(), report.publishedDate(), 0);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("X-Report-Id", report.reportId())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .body(report.content());
    }

    @GetMapping
    @Operation(
            summary = "보관된 입지 분석 보고서 목록 조회",
            description = "생성 시각 내림차순, 보고서 ID 내림차순으로 정렬된 요약 페이지를 반환합니다. "
                    + "기존 JSON 배열 응답은 reports와 페이지 메타데이터를 포함한 객체로 변경되었습니다. "
                    + "PDF·HTML 본문은 포함하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "보고서 요약 페이지 조회 성공"),
            @ApiResponse(responseCode = "400", description = "페이지 범위 또는 파라미터 형식 오류",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationAnalysisReportPageResponse list(
            @RequestParam String email,
            @Parameter(description = "1부터 시작하는 페이지", schema = @Schema(minimum = "1"))
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", schema = @Schema(minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), email);
        return archiveService.list(userId(user), page, limit);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "보관된 입지 분석 보고서 상세 조회")
    public LocationAnalysisReportResponse get(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), email);
        return archiveService.get(reportId, userId(user));
    }

    @GetMapping(value = "/{reportId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "보관된 입지 분석 PDF 다운로드")
    public ResponseEntity<byte[]> download(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), email);
        var report = archiveService.download(reportId, userId(user));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(
                        downloadFilename(report.reportName(), report.publishedDate(), report.version())))
                .body(report.content());
    }

    private String contentDisposition(String filename) {
        return "attachment; filename=\"" + filename + "\"";
    }

    private String downloadFilename(String reportName, LocalDate publishedDate, long version) {
        String safeReportName = asciiFilename(reportName);
        String date = publishedDate == null ? "undated" : publishedDate.toString();
        long displayVersion = Math.max(1, version + 1);
        return "%s-%s-foot-traffic-analysis-v%d.pdf".formatted(safeReportName, date, displayVersion);
    }

    private String asciiFilename(String reportName) {
        if (reportName == null || reportName.isBlank()) {
            return "location-analysis";
        }
        String sanitized = reportName
                .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "-")
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "")
                .trim();
        return sanitized.isBlank() ? "location-analysis" : sanitized;
    }

    @GetMapping(value = "/{reportId}/html", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "보관된 입지 분석 HTML 디버그 조회")
    public ResponseEntity<String> html(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), email);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(archiveService.html(
                        reportId,
                        userId(user)
                ));
    }

    @PatchMapping("/{reportId}")
    @Operation(summary = "보관된 입지 분석 보고서 정보 수정")
    public LocationAnalysisReportResponse update(
            @PathVariable String reportId,
            @RequestParam String email,
            @Valid @RequestBody LocationAnalysisReportUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), email);
        accessPolicy.requireOwnedEmail(userId(user), request.email());
        return archiveService.update(reportId, userId(user), request);
    }

    @DeleteMapping("/{reportId}")
    @Operation(summary = "보관된 입지 분석 보고서 삭제")
    public ResponseEntity<Void> delete(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), email);
        archiveService.delete(reportId, userId(user));
        return ResponseEntity.noContent().build();
    }

    private Long userId(JwtAuthenticatedUser user) {
        return user == null ? null : user.userId();
    }
}
