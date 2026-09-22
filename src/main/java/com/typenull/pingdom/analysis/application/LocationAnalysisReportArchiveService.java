package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportResponse;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportUpdateRequest;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService.LocationAnalysisPdf;
import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생성된 PDF·HTML과 입력 조건을 하나의 보고서로 보관하고 이메일 조건으로 조회합니다.
 * 이메일이 로그인 사용자 소유인지 확인하는 책임은 API의 접근 정책에 있으며, 목록은 본문을 제외한 projection을 사용합니다.
 * 제목·이메일 수정은 메타데이터에만 적용되어 기존 PDF·HTML 내용은 다시 생성되지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class LocationAnalysisReportArchiveService {

    private final LocationAnalysisReportRepository reportRepository;
    private final Clock clock;

    /**
     * 이미 생성된 PDF·HTML과 요청 조건, 이메일 및 동의 여부를 하나의 보고서 행으로 저장하고 요약 응답을 반환한다.
     * 이메일 소유권 확인과 PDF 생성은 호출자가 먼저 수행하며 저장 실패에 대한 재생성·재시도는 하지 않는다.
     */
    @Transactional
    public LocationAnalysisReportResponse archive(LocationAnalysisRequest request, LocationAnalysisPdf pdf) {
        LocationAnalysisReport report = LocationAnalysisReport.create(
                pdf.reportId(), pdf.reportName(), request.getCategory(), request.getRegion(),
                request.getTargetCustomerGroup(), request.getOperatingHours(), normalizeEmail(request.getEmail()),
                Boolean.TRUE.equals(request.getPrivacyConsent()), pdf.publishedDate(),
                pdf.analysisBasisDate(), pdf.html(), pdf.content(), LocalDateTime.now(clock)
        );
        return LocationAnalysisReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<LocationAnalysisReportResponse> list(String email) {
        return reportRepository.findSummariesByEmail(normalizeEmail(email)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationAnalysisReportResponse get(String reportId, String email) {
        return reportRepository.findSummaryByReportIdAndEmail(reportId, normalizeEmail(email))
                .map(this::toResponse)
                .orElseThrow(() -> notFound());
    }

    @Transactional(readOnly = true)
    public LocationAnalysisPdfDownload download(String reportId, String email) {
        LocationAnalysisReport report = find(reportId, email);
        return new LocationAnalysisPdfDownload(
                report.getReportId(), report.getReportName(), report.getPublishedDate(), report.getVersion(),
                report.getPdfContent()
        );
    }

    @Transactional(readOnly = true)
    public String html(String reportId, String email) {
        return reportRepository.findHtmlByReportIdAndEmail(reportId, normalizeEmail(email))
                .orElseThrow(() -> notFound());
    }

    /**
     * 보고서 ID와 기존 이메일이 모두 일치하는 행의 제목·이메일·변경 시각을 갱신한다.
     * 대상이 없으면 동일한 보고서 없음 오류를 반환하고, 이미 보관된 PDF·HTML은 다시 생성하지 않는다.
     */
    @Transactional
    public LocationAnalysisReportResponse update(
            String reportId,
            String email,
            LocationAnalysisReportUpdateRequest request
    ) {
        LocationAnalysisReport report = find(reportId, email);
        report.update(request.reportName(), normalizeEmail(request.email()), LocalDateTime.now(clock));
        return LocationAnalysisReportResponse.from(report);
    }

    @Transactional
    public void delete(String reportId, String email) {
        reportRepository.delete(find(reportId, email));
    }

    private LocationAnalysisReport find(String reportId, String email) {
        return reportRepository.findByReportIdAndEmail(reportId, normalizeEmail(email))
                .orElseThrow(this::notFound);
    }

    private AnalysisReportException notFound() {
        return new AnalysisReportException(AnalysisReportErrorCode.ANALYSIS_REPORT_NOT_FOUND, null);
    }

    public record LocationAnalysisPdfDownload(
            String reportId,
            String reportName,
            java.time.LocalDate publishedDate,
            long version,
            byte[] content
    ) {
        public LocationAnalysisPdfDownload(String reportId, byte[] content) {
            this(reportId, null, null, 0, content);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private LocationAnalysisReportResponse toResponse(
            LocationAnalysisReportRepository.SummaryView report
    ) {
        return LocationAnalysisReportResponse.fromSummary(
                report.getReportId(), report.getReportName(), report.getCategory(), report.getRegion(),
                report.getTargetCustomerGroup(), report.getOperatingHours(), report.getEmail(),
                report.isPrivacyConsent(), report.getPublishedDate(), report.getAnalysisBasisDate(),
                report.getCreatedAt()
        );
    }
}
