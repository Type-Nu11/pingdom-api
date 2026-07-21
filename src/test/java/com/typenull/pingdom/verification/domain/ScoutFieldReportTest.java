package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutFieldReportTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 21, 15, 0);

    @Test
    void submittedReportStartsWithoutReviewData() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L,
                2L,
                ScoutFieldReportType.OPERATING_HOURS,
                " 영업시간이 다릅니다. ",
                null,
                now
        );

        assertThat(report.getStatus()).isEqualTo(ScoutFieldReportStatus.SUBMITTED);
        assertThat(report.getDescription()).isEqualTo("영업시간이 다릅니다.");
        assertThat(report.getReviewerAdminUserId()).isNull();
        assertThat(report.getReviewedAt()).isNull();
    }

    @Test
    void submittedReportCanBeAccepted() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.PLACE_INFORMATION, "현장 정보가 다릅니다.", null, now
        );

        report.review(9L, ScoutFieldReportStatus.ACCEPTED, null, now.plusMinutes(10));

        assertThat(report.getStatus()).isEqualTo(ScoutFieldReportStatus.ACCEPTED);
        assertThat(report.getReviewerAdminUserId()).isEqualTo(9L);
        assertThat(report.getReviewedAt()).isEqualTo(now.plusMinutes(10));
    }

    @Test
    void rejectionRequiresReviewNote() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.SAFETY, "안전 확인이 필요합니다.", null, now
        );

        assertThatThrownBy(() -> report.review(
                9L, ScoutFieldReportStatus.REJECTED, " ", now.plusMinutes(10)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewedReportCannotBeReviewedAgain() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.CLOSED_PLACE, "폐업한 것 같습니다.", null, now
        );
        report.review(9L, ScoutFieldReportStatus.REJECTED, "현장 확인 결과 운영 중", now.plusMinutes(10));

        assertThatThrownBy(() -> report.review(
                9L, ScoutFieldReportStatus.ACCEPTED, null, now.plusMinutes(20)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankDescriptionIsRejected() {
        assertThatThrownBy(() -> ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.OTHER, "  ", null, now
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
