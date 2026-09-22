package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import com.typenull.pingdom.verification.api.dto.MyVisitorVerificationReportResponse;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 15, 0);

    /** 새 제보는 SUBMITTED 상태로 시작하며 심사자·심사 시각은 null이어야 한다. */
    @Test
    void submitWithoutReviewData() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OPERATING_HOURS, "영업시간이 다릅니다.", null, now);

        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(report.getReviewerAdminUserId()).isNull();
        assertThat(report.getReviewedAt()).isNull();
    }

    /** 승인하면 ACCEPTED 상태와 관리자 9, 제출 10분 뒤 심사 시각이 기록된다. */
    @Test
    void acceptSubmittedReport() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LOCATION, "위치가 다릅니다.", null, now);

        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, "현장 자료 확인", now.plusMinutes(10));

        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.ACCEPTED);
        assertThat(report.getReviewerAdminUserId()).isEqualTo(9L);
        assertThat(report.getReviewedAt()).isEqualTo(now.plusMinutes(10));
    }

    /** 거절 시 공백뿐인 심사 메모는 인자 오류로 거부한다. */
    @Test
    void requireRejectionNote() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null, now);

        assertThatThrownBy(() -> report.review(9L, VisitorVerificationReportStatus.REJECTED, " ", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 이미 거절된 제보에 다시 승인 요청을 하면 상태 전이 오류로 거부한다. */
    @Test
    void rejectRepeatedReportReview() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.CLOSED_PLACE, "폐업했습니다.", null, now);
        report.review(9L, VisitorVerificationReportStatus.REJECTED, "운영 중 확인", now.plusMinutes(10));

        assertThatThrownBy(() -> report.review(
                9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 작성자 응답은 거절 사유를 제공하지만 record에 심사자 ID와 내부 reviewNote 필드는 없어야 한다. */
    @Test
    void exposeUserRejectionReason() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null, now);
        report.review(9L, VisitorVerificationReportStatus.REJECTED, "증빙 불충분", now.plusMinutes(10));

        MyVisitorVerificationReportResponse response = MyVisitorVerificationReportResponse.from(report);

        assertThat(response.rejectionReason()).isEqualTo("증빙 불충분");
        assertThat(MyVisitorVerificationReportResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("reviewerAdminUserId", "reviewNote");
    }

    /** WAIT_TIME 제보는 전달한 35분을 보관하며 언어 값은 null을 유지한다. */
    @Test
    void retainWaitTimeValue() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "대기 시간 제보", null,
                35, null, null, null, now);

        assertThat(report.getWaitTimeMinutes()).isEqualTo(35);
        assertThat(report.getLanguageCode()).isNull();
    }

    /** 대기 시간의 양쪽 경계 0분과 1,440분을 모두 허용한다. */
    @Test
    void acceptWaitTimeBoundaries() {
        assertThat(VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "즉시 입장", null,
                0, null, null, null, now).getWaitTimeMinutes()).isZero();
        assertThat(VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "하루 대기", null,
                1440, null, null, null, now).getWaitTimeMinutes()).isEqualTo(1440);
    }

    /** 대기 시간 -1분과 1,441분은 인자 오류로 거부한다. */
    @Test
    void rejectInvalidWaitTime() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "잘못된 대기", null,
                -1, null, null, null, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "잘못된 대기", null,
                1441, null, null, null, now)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 언어 태그 앞뒤 공백을 제거해 en-US로 저장하는지 확인한다. */
    @Test
    void normalizeLanguageCode() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "언어 지원 제보", null,
                null, " en-US ", null, null, now);

        assertThat(report.getLanguageCode()).isEqualTo("en-US");
    }

    /** 지정 언어 태그 형식에 맞지 않는 english를 거부한다. */
    @Test
    void rejectInvalidLanguageCode() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "언어 지원 제보", null,
                null, "english", null, null, now)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 혼잡도 유형에 대기 시간만 전달하면 유형에 맞지 않아 거부한다. */
    @Test
    void rejectMismatchedStructuredValue() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.CROWD_LEVEL, "혼잡도 제보", null,
                20, null, null, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 대기 시간 유형에 대기 시간과 혼잡도를 함께 제공하면 인자 오류로 거부한다. */
    @Test
    void rejectMixedStructuredValues() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.WAIT_TIME, "복수 값 제보", null,
                20, null, null, CrowdLevel.HIGH, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 구조화 값이 없는 LOCATION 유형에 쿠폰 사용 상태를 제공하면 거부한다. */
    @Test
    void rejectUnexpectedStructuredValue() {
        assertThatThrownBy(() -> VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.LOCATION, "위치 제보", null,
                null, null, CouponUsageStatus.AVAILABLE, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 거절된 제보에 정정을 제출해도 원본 본문은 유지해야 한다.
     * 새 본문은 별도 정정 객체에 SUBMITTED 상태로 보관된다.
     */
    @Test
    void preserveReportUntilCorrectionReview() {
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

    /**
     * 정정을 승인하고 명시적으로 원본에 적용하면 정정은 ACCEPTED, 원본은 SUBMITTED가 된다.
     * 원본 본문·대기 시간은 새 값으로 바뀌고 기존 심사자 ID는 비워야 한다.
     */
    @Test
    void resubmitCorrectedReport() {
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

    /** 아직 심사되지 않은 원본 제보에는 정정을 제출할 수 없다. */
    @Test
    void rejectPendingReportCorrection() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null, now);

        assertThatThrownBy(() -> VisitorVerificationReportCorrection.submit(
                report, 1L, "정정 내용", null, null, null, null, null, now.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 정정 거절에 공백 사유를 전달하면 인자 오류로 거부한다. */
    @Test
    void requireCorrectionRejectionNote() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.OTHER, "기존 내용", null, now);
        report.review(9L, VisitorVerificationReportStatus.ACCEPTED, null, now.plusMinutes(10));
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "정정 내용", null, null, null, null, null, now.plusMinutes(20));

        assertThatThrownBy(() -> correction.review(
                9L, VisitorVerificationReportCorrectionStatus.REJECTED, " ", now.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 이미 승인한 정정을 다시 심사하면 상태 오류로 거부한다. */
    @Test
    void rejectRepeatedCorrectionReview() {
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

    /** 정정 심사 결과로 SUBMITTED를 전달하면 허용된 결정이 아니므로 거부한다. */
    @Test
    void rejectSubmittedReviewDecision() {
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
