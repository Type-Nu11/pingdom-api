package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportUpdateRequest;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService.LocationAnalysisPdf;
import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class LocationAnalysisReportArchiveServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private final LocationAnalysisReportRepository repository = mock(LocationAnalysisReportRepository.class);
    private final LocationAnalysisReportArchiveService service =
            new LocationAnalysisReportArchiveService(repository, CLOCK);

    @Test
    void archivesUnderAuthenticatedUserIdRatherThanContactEmail() {
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setEmail(" Owner@Example.com ");
        request.setCategory("카페");
        request.setRegion("서울");
        request.setPrivacyConsent(true);
        LocationAnalysisPdf pdf = new LocationAnalysisPdf(new byte[]{1, 2}, "report-1", "분석");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.archive(7L, request, pdf);

        ArgumentCaptor<LocationAnalysisReport> captor = ArgumentCaptor.forClass(LocationAnalysisReport.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getEmail()).isEqualTo("owner@example.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsMissingOrInvalidOwnerBeforeAnyPersistenceAccess(Long ownerUserId) {
        LocationAnalysisReportUpdateRequest request = new LocationAnalysisReportUpdateRequest("수정", "new@example.com");
        assertForbidden(() -> service.archive(ownerUserId, null, null));
        assertForbidden(() -> service.list(ownerUserId, 1, 20));
        assertForbidden(() -> service.get("report-1", ownerUserId));
        assertForbidden(() -> service.download("report-1", ownerUserId));
        assertForbidden(() -> service.html("report-1", ownerUserId));
        assertForbidden(() -> service.update("report-1", ownerUserId, request));
        assertForbidden(() -> service.delete("report-1", ownerUserId));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAllSingleReportOperationsWhenOwnerScopedQueryFindsNothing() {
        when(repository.findSummaryByReportIdAndOwnerUserId("report-1", 8L)).thenReturn(Optional.empty());
        when(repository.findHtmlByReportIdAndOwnerUserId("report-1", 8L)).thenReturn(Optional.empty());
        when(repository.findMetadataByReportIdAndOwnerUserId("report-1", 8L)).thenReturn(Optional.empty());
        when(repository.findPdfByReportIdAndOwnerUserId("report-1", 8L)).thenReturn(Optional.empty());

        assertNotFound(() -> service.get("report-1", 8L));
        assertNotFound(() -> service.html("report-1", 8L));
        assertNotFound(() -> service.download("report-1", 8L));
        assertNotFound(() -> service.update("report-1", 8L,
                new LocationAnalysisReportUpdateRequest("수정", "same@example.com")));
        assertNotFound(() -> service.delete("report-1", 8L));
    }

    @Test
    void updatesContactWithinOwnerAndVersionScopeWithoutLoadingStoredPayload() {
        var metadata = metadata();
        var updated = summary("report-1");
        when(updated.getReportName()).thenReturn("수정");
        when(updated.getEmail()).thenReturn("new@example.com");
        when(repository.findMetadataByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(metadata));
        when(repository.updateMetadataIfVersionMatches("report-1", 7L, 3L,
                "수정", "new@example.com", LocalDateTime.now(CLOCK))).thenReturn(1);
        when(repository.findSummaryByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(updated));

        var result = service.update("report-1", 7L,
                new LocationAnalysisReportUpdateRequest("수정", " New@Example.com "));

        assertThat(result.reportName()).isEqualTo("수정");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.downloadPath()).isEqualTo("/analysis/reports/report-1/download");
        verify(repository).updateMetadataIfVersionMatches("report-1", 7L, 3L,
                "수정", "new@example.com", LocalDateTime.now(CLOCK));
        verify(repository, never()).findByReportIdAndOwnerUserId(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsConcurrentMetadataChangeRatherThanOverwritingNewerVersion() {
        var metadata = metadata();
        when(repository.findMetadataByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(metadata));

        assertThatThrownBy(() -> service.update("report-1", 7L,
                new LocationAnalysisReportUpdateRequest("수정", "new@example.com")))
                .isInstanceOfSatisfying(ObjectOptimisticLockingFailureException.class, exception -> {
                    assertThat(exception.getPersistentClass()).isEqualTo(LocationAnalysisReport.class);
                    assertThat(exception.getIdentifier()).isEqualTo("report-1");
                });
        verify(repository, never()).findSummaryByReportIdAndOwnerUserId(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void doesNotIncreaseVersionWhenAllMetadataAndClockTimeAreUnchanged() {
        var metadata = metadata();
        when(metadata.getUpdatedAt()).thenReturn(LocalDateTime.now(CLOCK));
        when(repository.findMetadataByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(metadata));

        var result = service.update("report-1", 7L,
                new LocationAnalysisReportUpdateRequest("분석", " OLD@EXAMPLE.COM "));

        assertThat(result.reportName()).isEqualTo("분석");
        verify(repository).findMetadataByReportIdAndOwnerUserId("report-1", 7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deletesOnlyTheOwnerScopedVersionReadByTheRequest() {
        var metadata = metadata();
        when(repository.findMetadataByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(metadata));
        when(repository.deleteIfVersionMatches("report-1", 7L, 3L)).thenReturn(1);

        service.delete("report-1", 7L);

        verify(repository).findMetadataByReportIdAndOwnerUserId("report-1", 7L);
        verify(repository).deleteIfVersionMatches("report-1", 7L, 3L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsConcurrentDeleteRatherThanSilentlySucceeding() {
        var metadata = metadata();
        when(repository.findMetadataByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(metadata));

        assertThatThrownBy(() -> service.delete("report-1", 7L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void downloadsPdfWithTheFilenameMetadataAndVersion() {
        var pdf = mock(LocationAnalysisReportRepository.PdfView.class);
        LocalDate publishedDate = LocalDate.of(2026, 11, 12);
        when(pdf.getReportId()).thenReturn("report-1");
        when(pdf.getReportName()).thenReturn("분석");
        when(pdf.getPublishedDate()).thenReturn(publishedDate);
        when(pdf.getVersion()).thenReturn(3L);
        when(pdf.getPdfContent()).thenReturn(new byte[]{1, 2});
        when(repository.findPdfByReportIdAndOwnerUserId("report-1", 7L)).thenReturn(Optional.of(pdf));

        var result = service.download("report-1", 7L);

        assertThat(result.reportId()).isEqualTo("report-1");
        assertThat(result.reportName()).isEqualTo("분석");
        assertThat(result.publishedDate()).isEqualTo(publishedDate);
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.content()).containsExactly(1, 2);
        verify(repository).findPdfByReportIdAndOwnerUserId("report-1", 7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void listsOnlyOwnerScopedSummariesWithinRequestedPage() {
        var first = summary("report-2");
        var second = summary("report-1");
        var pageable = PageRequest.of(1, 2);
        when(repository.findSummariesByOwnerUserId(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 5));

        var result = service.list(7L, 2, 2);

        assertThat(result.reports()).extracting(response -> response.reportId())
                .containsExactly("report-2", "report-1");
        assertThat(result.reports().getFirst().downloadPath()).isEqualTo("/analysis/reports/report-2/download");
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        verify(repository).findSummariesByOwnerUserId(7L, pageable);
    }

    @Test
    void preservesTotalForAnEmptyOutOfRangePage() {
        var pageable = PageRequest.of(5, 100);
        when(repository.findSummariesByOwnerUserId(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 3));

        var result = service.list(7L, 6, 100);

        assertThat(result.reports()).isEmpty();
        assertThat(result.page()).isEqualTo(6);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0,20", "-1,20", "1,0", "1,-1", "1,101", "2147483647,100"})
    void rejectsInvalidPageBeforePersistenceAccess(int page, int limit) {
        assertError(() -> service.list(7L, page, limit), AnalysisReportErrorCode.ANALYSIS_REPORT_INVALID_PAGINATION);
        verifyNoInteractions(repository);
    }

    private LocationAnalysisReportRepository.SummaryView summary(String reportId) {
        var summary = mock(LocationAnalysisReportRepository.SummaryView.class);
        when(summary.getReportId()).thenReturn(reportId);
        return summary;
    }

    private LocationAnalysisReportRepository.MetadataView metadata() {
        var metadata = mock(LocationAnalysisReportRepository.MetadataView.class);
        when(metadata.getReportId()).thenReturn("report-1");
        when(metadata.getReportName()).thenReturn("분석");
        when(metadata.getEmail()).thenReturn("old@example.com");
        when(metadata.getVersion()).thenReturn(3L);
        when(metadata.getUpdatedAt()).thenReturn(LocalDateTime.now(CLOCK).minusMinutes(1));
        return metadata;
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertError(action, AnalysisReportErrorCode.ANALYSIS_REPORT_FORBIDDEN);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertError(action, AnalysisReportErrorCode.ANALYSIS_REPORT_NOT_FOUND);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, AnalysisReportErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AnalysisReportException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
