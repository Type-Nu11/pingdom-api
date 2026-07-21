package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import com.typenull.pingdom.verification.api.dto.MyVisitorVerificationReportResponse;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 15, 0);

    @Test
    void submittedReportStartsWithoutReviewData() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OPERATING_HOURS, "영업시간이 다릅니다.", null, now);

        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(report.getReviewerAdminUserId()).isNull();
        assertThat(report.getReviewedAt()).isNull();
    }

    @Test
    void submittedReportCanBeAccepted() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LOCATION, "위치가 다릅니다.", null, now);

        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, "현장 자료 확인", now.plusMinutes(10));

        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.ACCEPTED);
        assertThat(report.getReviewerAdminUserId()).isEqualTo(9L);
        assertThat(report.getReviewedAt()).isEqualTo(now.plusMinutes(10));
    }

    @Test
    void rejectionRequiresReviewNote() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null, now);

        assertThatThrownBy(() -> report.review(9L, VisitorVerificationReportStatus.REJECTED, " ", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewedReportCannotBeReviewedAgain() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.CLOSED_PLACE, "폐업했습니다.", null, now);
        report.review(9L, VisitorVerificationReportStatus.REJECTED, "운영 중 확인", now.plusMinutes(10));

        assertThatThrownBy(() -> report.review(
                9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userResponseExposesRejectionReasonWithoutAdminIdentity() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null, now);
        report.review(9L, VisitorVerificationReportStatus.REJECTED, "증빙 불충분", now.plusMinutes(10));

        MyVisitorVerificationReportResponse response = MyVisitorVerificationReportResponse.from(report);

        assertThat(response.rejectionReason()).isEqualTo("증빙 불충분");
        assertThat(MyVisitorVerificationReportResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("reviewerAdminUserId", "reviewNote");
    }

    @Test
    void waitTimeReportRequiresOnlyBoundedMinutes() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "대기 시간 제보", null,
                35, null, null, null, now);

        assertThat(report.getWaitTimeMinutes()).isEqualTo(35);
        assertThat(report.getLanguageCode()).isNull();
    }

    @Test
    void waitTimeReportAcceptsBoundaryValues() {
        assertThat(VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "즉시 입장", null,
                0, null, null, null, now).getWaitTimeMinutes()).isZero();
        assertThat(VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "하루 대기", null,
                1440, null, null, null, now).getWaitTimeMinutes()).isEqualTo(1440);
    }

    @Test
    void waitTimeReportRejectsOutOfRangeValues() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "잘못된 대기", null,
                -1, null, null, null, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "잘못된 대기", null,
                1441, null, null, null, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void languageReportRequiresNormalizedLanguageCode() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "언어 지원 제보", null,
                null, " en-US ", null, null, now);

        assertThat(report.getLanguageCode()).isEqualTo("en-US");
    }

    @Test
    void languageReportRejectsInvalidCode() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "언어 지원 제보", null,
                null, "english", null, null, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredReportRejectsMismatchedValue() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.CROWD_LEVEL, "혼잡도 제보", null,
                20, null, null, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredReportRejectsMultipleValues() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "복수 값 제보", null,
                20, null, null, CrowdLevel.HIGH, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyReportRejectsStructuredValue() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LOCATION, "위치 제보", null,
                null, null, CouponUsageStatus.AVAILABLE, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewedReportCanReceiveCorrectionWithoutOverwritingUntilAcceptance() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OPERATING_HOURS, "기존 영업시간", null, now);
        report.review(9L, VisitorVerificationReportStatus.REJECTED, "최신 증빙 필요", now.plusMinutes(10));

        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "수정된 영업시간", "https://example.com/evidence", null,
                null, null, null, now.plusMinutes(20));

        assertThat(report.getDescription()).isEqualTo("기존 영업시간");
        assertThat(correction.getDescription()).isEqualTo("수정된 영업시간");
        assertThat(correction.getStatus()).isEqualTo(VisitorVerificationReportCorrectionStatus.SUBMITTED);
    }

    @Test
    void acceptingCorrectionResubmitsOriginalReportForNormalReviewFlow() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "기존 대기", null,
                60, null, null, null, now);
        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(10));
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "수정된 대기", null, 20,
                null, null, null, now.plusMinutes(20));

        correction.review(9L, VisitorVerificationReportCorrectionStatus.ACCEPTED, null, now.plusMinutes(30));
        report.applyCorrection(
                correction.getDescription(), correction.getEvidenceUrl(), correction.getWaitTimeMinutes(),
                correction.getLanguageCode(), correction.getCouponUsageStatus(), correction.getCrowdLevel(),
                now.plusMinutes(30));

        assertThat(correction.getStatus()).isEqualTo(VisitorVerificationReportCorrectionStatus.ACCEPTED);
        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(report.getDescription()).isEqualTo("수정된 대기");
        assertThat(report.getWaitTimeMinutes()).isEqualTo(20);
        assertThat(report.getReviewerAdminUserId()).isNull();
    }

    @Test
    void correctionCannotBeSubmittedForPendingReport() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null, now);

        assertThatThrownBy(() -> VisitorVerificationReportCorrection.submit(
                report, 1L, "정정 내용", null, null, null, null, null, now.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedCorrectionRequiresReviewNote() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "기존 내용", null, now);
        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(10));
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "정정 내용", null, null, null, null, null, now.plusMinutes(20));

        assertThatThrownBy(() -> correction.review(
                9L, VisitorVerificationReportCorrectionStatus.REJECTED, " ", now.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewedCorrectionCannotBeReviewedAgain() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "기존 내용", null, now);
        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(10));
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "정정 내용", null, null, null, null, null, now.plusMinutes(20));
        correction.review(9L, VisitorVerificationReportCorrectionStatus.ACCEPTED, null, now.plusMinutes(30));

        assertThatThrownBy(() -> correction.review(
                9L, VisitorVerificationReportCorrectionStatus.ACCEPTED, null, now.plusMinutes(40)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submittedIsNotAValidCorrectionReviewDecision() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "기존 내용", null, now);
        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(10));
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "정정 내용", null, null, null, null, null, now.plusMinutes(20));

        assertThatThrownBy(() -> correction.review(
                9L, VisitorVerificationReportCorrectionStatus.SUBMITTED, null, now.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
