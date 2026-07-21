package com.typenull.pingdom.place.domain.place.information.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceInformationReportTest {

    @Test
    void submittedReportCanBeReviewedAcceptedAndDisputed() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 7, 21, 10, 0);
        PlaceInformationReport report = PlaceInformationReport.submit(
                place(),
                null,
                10L,
                PlaceInformationReportTargetType.ADDRESS,
                PlaceInformationReportReasonType.INCORRECT,
                "  주소가 실제 위치와 다릅니다.  ",
                " https://example.com/address-proof ",
                submittedAt
        );

        assertThat(report.getStatus()).isEqualTo(PlaceInformationReportStatus.SUBMITTED);
        assertThat(report.getDescription()).isEqualTo("주소가 실제 위치와 다릅니다.");
        assertThat(report.getEvidenceUrl()).isEqualTo("https://example.com/address-proof");

        LocalDateTime acceptedAt = submittedAt.plusHours(1);
        report.accept(99L, "현장 자료와 일치", acceptedAt);

        assertThat(report.getStatus()).isEqualTo(PlaceInformationReportStatus.ACCEPTED);
        assertThat(report.getReviewedByAdminUserId()).isEqualTo(99L);
        assertThat(report.getReviewedAt()).isEqualTo(acceptedAt);

        LocalDateTime disputedAt = acceptedAt.plusHours(2);
        PlaceInformationReportDispute dispute = report.submitDispute(
                20L,
                "관리자가 확인한 자료가 오래된 정보입니다.",
                "https://example.com/current-proof",
                disputedAt
        );

        assertThat(report.getStatus()).isEqualTo(PlaceInformationReportStatus.DISPUTED);
        assertThat(report.currentDisputes()).containsExactly(dispute);
        assertThat(dispute.getStatus()).isEqualTo(PlaceInformationDisputeStatus.SUBMITTED);
        assertThat(dispute.getDescription()).isEqualTo("관리자가 확인한 자료가 오래된 정보입니다.");
    }

    @Test
    void reportReviewRequiresReasonAndTerminalReportCannotBeReviewedAgain() {
        PlaceInformationReport report = PlaceInformationReport.submit(
                place(),
                null,
                10L,
                PlaceInformationReportTargetType.OPERATING_STATUS,
                PlaceInformationReportReasonType.OUTDATED,
                "영업 상태가 오래되었습니다.",
                null,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );

        assertThatThrownBy(() -> report.reject(99L, " ", LocalDateTime.of(2026, 7, 21, 11, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        report.reject(99L, "증빙 부족", LocalDateTime.of(2026, 7, 21, 11, 0));

        assertThatThrownBy(() -> report.startReview(99L, LocalDateTime.of(2026, 7, 21, 12, 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> report.submitDispute(
                20L,
                "반박합니다.",
                null,
                LocalDateTime.of(2026, 7, 21, 12, 0)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disputeReviewRequiresReasonAndCannotBeProcessedTwice() {
        PlaceInformationReport report = PlaceInformationReport.submit(
                place(),
                null,
                10L,
                PlaceInformationReportTargetType.SOURCE_EVIDENCE,
                PlaceInformationReportReasonType.MISLEADING,
                "출처가 맞지 않습니다.",
                null,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );
        report.accept(99L, "신고 인정", LocalDateTime.of(2026, 7, 21, 11, 0));
        PlaceInformationReportDispute dispute = report.submitDispute(
                20L,
                "출처는 공식 자료입니다.",
                null,
                LocalDateTime.of(2026, 7, 21, 12, 0)
        );

        assertThatThrownBy(() -> dispute.accept(99L, " ", LocalDateTime.of(2026, 7, 21, 13, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        dispute.accept(99L, "반박 인정", LocalDateTime.of(2026, 7, 21, 13, 0));

        assertThat(dispute.getStatus()).isEqualTo(PlaceInformationDisputeStatus.ACCEPTED);
        assertThatThrownBy(() -> dispute.reject(99L, "번복", LocalDateTime.of(2026, 7, 21, 14, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    private MapPlace place() {
        return MapPlace.builder()
                .id(1L)
                .name("신고 대상 장소")
                .address("경상남도 진주시 테스트로 1")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(1L)
                .registrant("report-owner")
                .build();
    }
}
