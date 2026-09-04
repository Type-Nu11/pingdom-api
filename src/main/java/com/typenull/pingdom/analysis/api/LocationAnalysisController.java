package com.typenull.pingdom.analysis.api;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportAccessPolicy;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveService;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportResponse;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportUpdateRequest;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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

@RestController
@RequestMapping("/analysis/reports")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class LocationAnalysisController {

    private final LocationAnalysisReportService reportService;
    private final LocationAnalysisReportArchiveService archiveService;
    private final LocationAnalysisReportAccessPolicy accessPolicy;

    @PostMapping(value = "/location", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "입지 분석 PDF 보고서 생성", description = "입력 조건을 AI/MCP 분석 인터페이스로 전달하고 HTML 분석 결과를 PDF로 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF 보고서 생성 성공"),
            @ApiResponse(responseCode = "400", description = "지역 누락 또는 입력값 검증 실패", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 분석 응답 처리 실패", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<byte[]> generate(
            @Valid @RequestBody LocationAnalysisRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        accessPolicy.requireOwnedEmail(userId(user), request.getEmail());
        LocationAnalysisReportService.LocationAnalysisPdf report = reportService.generate(request);
        archiveService.archive(request, report);
        String filename = downloadFilename(report.reportName(), report.publishedDate(), 0);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("X-Report-Id", report.reportId())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .body(report.content());
    }

    @GetMapping
    @Operation(summary = "보관된 입지 분석 보고서 목록 조회")
    public List<LocationAnalysisReportResponse> list(
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return archiveService.list(accessPolicy.requireOwnedEmail(userId(user), email));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "보관된 입지 분석 보고서 상세 조회")
    public LocationAnalysisReportResponse get(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return archiveService.get(reportId, accessPolicy.requireOwnedEmail(userId(user), email));
    }

    @GetMapping(value = "/{reportId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "보관된 입지 분석 PDF 다운로드")
    public ResponseEntity<byte[]> download(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        var report = archiveService.download(
                reportId,
                accessPolicy.requireOwnedEmail(userId(user), email)
        );
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
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(archiveService.html(
                        reportId,
                        accessPolicy.requireOwnedEmail(userId(user), email)
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
        String accountEmail = accessPolicy.requireOwnedEmail(userId(user), email);
        accessPolicy.requireOwnedEmail(userId(user), request.email());
        return archiveService.update(reportId, accountEmail, request);
    }

    @DeleteMapping("/{reportId}")
    @Operation(summary = "보관된 입지 분석 보고서 삭제")
    public ResponseEntity<Void> delete(
            @PathVariable String reportId,
            @RequestParam String email,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        archiveService.delete(reportId, accessPolicy.requireOwnedEmail(userId(user), email));
        return ResponseEntity.noContent().build();
    }

    private Long userId(JwtAuthenticatedUser user) {
        return user == null ? null : user.userId();
    }
}
