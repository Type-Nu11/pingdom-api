package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutFieldReportTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 21, 15, 0);

    /**
     * 새 제보는 SUBMITTED 상태이고 본문 앞뒤 공백이 제거되어야 한다.
     * 아직 심사하지 않았으므로 심사자와 심사 시각은 null이다.
     */
    @Test
    void submitWithoutReviewData() {
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

    /** 제출된 제보를 승인하면 ACCEPTED 상태와 관리자 9, 제출 10분 뒤 심사 시각을 기록한다. */
    @Test
    void acceptSubmittedReport() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.PLACE_INFORMATION, "현장 정보가 다릅니다.", null, now
        );

        report.review(9L, ScoutFieldReportStatus.ACCEPTED, null, now.plusMinutes(10));

        assertThat(report.getStatus()).isEqualTo(ScoutFieldReportStatus.ACCEPTED);
        assertThat(report.getReviewerAdminUserId()).isEqualTo(9L);
        assertThat(report.getReviewedAt()).isEqualTo(now.plusMinutes(10));
    }

    /** 거절에 공백뿐인 심사 메모를 주면 인자 오류로 거부한다. */
    @Test
    void requireRejectionNote() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.SAFETY, "안전 확인이 필요합니다.", null, now
        );

        assertThatThrownBy(() -> report.review(
                9L, ScoutFieldReportStatus.REJECTED, " ", now.plusMinutes(10)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** 이미 거절한 제보를 다시 승인하려 하면 상태 전이 오류로 거부한다. */
    @Test
    void rejectRepeatedReview() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.CLOSED_PLACE, "폐업한 것 같습니다.", null, now
        );
        report.review(9L, ScoutFieldReportStatus.REJECTED, "현장 확인 결과 운영 중", now.plusMinutes(10));

        assertThatThrownBy(() -> report.review(
                9L, ScoutFieldReportStatus.ACCEPTED, null, now.plusMinutes(20)
        )).isInstanceOf(IllegalStateException.class);
    }

    /** 제출 본문이 공백뿐이면 인자 오류로 거부한다. */
    @Test
    void rejectBlankDescription() {
        assertThatThrownBy(() -> ScoutFieldReport.submit(
                1L, 2L, ScoutFieldReportType.OTHER, "  ", null, now
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
