package com.typenull.pingdom.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityReportTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 15, 12, 0);
    private final CommunityPost post = CommunityPost.create("travel", "제목", "내용", 1L);

    @ParameterizedTest
    @EnumSource(CommunityReportReason.class)
    void 모든_사유로_게시글_신고를_접수한다(CommunityReportReason reason) {
        CommunityReport report = CommunityReport.reportPost(2L, post, reason, " 설명 ", now);

        assertThat(report.getPost()).isSameAs(post);
        assertThat(report.getComment()).isNull();
        assertThat(report.getReporterUserId()).isEqualTo(2L);
        assertThat(report.getReason()).isEqualTo(reason);
        assertThat(report.getDescription()).isEqualTo("설명");
        assertThat(report.getStatus()).isEqualTo(CommunityReportStatus.PENDING);
        assertThat(report.getProcessedAt()).isNull();
        assertThat(report.getProcessedByAdminUserId()).isNull();
    }

    @Test
    void 댓글_신고는_댓글만_대상으로_참조한다() {
        CommunityPostComment comment = CommunityPostComment.create(post, 3L, "댓글");
        CommunityReport report = CommunityReport.reportComment(2L, comment, CommunityReportReason.SPAM, "설명", now);

        assertThat(report.getComment()).isSameAs(comment);
        assertThat(report.getPost()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void 빈_설명을_거절한다(String description) {
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, post, CommunityReportReason.OTHER, description, now))
                .isInstanceOf(CommunityException.class)
                .extracting("errorCode").isEqualTo(CommunityErrorCode.INVALID_REPORT);
    }

    @Test
    void 설명_길이_경계를_검증한다() {
        assertThat(CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "가".repeat(500), now)
                .getDescription()).hasSize(500);
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "가".repeat(501), now))
                .isInstanceOf(CommunityException.class);
    }

    @Test
    void 필수_신고_정보를_검증한다() {
        assertThatThrownBy(() -> CommunityReport.reportPost(null, post, CommunityReportReason.SPAM, "설명", now))
                .isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> CommunityReport.reportPost(0L, post, CommunityReportReason.SPAM, "설명", now))
                .isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, null, CommunityReportReason.SPAM, "설명", now))
                .isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> CommunityReport.reportComment(2L, null, CommunityReportReason.SPAM, "설명", now))
                .isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, post, null, "설명", now))
                .isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "설명", null))
                .isInstanceOf(CommunityException.class);
    }

    @ParameterizedTest
    @EnumSource(value = CommunityReportStatus.class, names = {"ACCEPTED", "DECLINED"})
    void 처리_정보를_기록하고_모든_재처리를_거절한다(CommunityReportStatus status) {
        CommunityReport report = CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "설명", now);
        if (status == CommunityReportStatus.ACCEPTED) {
            report.accept(9L, now.plusMinutes(1));
        } else {
            report.decline(9L, now.plusMinutes(1));
        }

        assertThat(report.getStatus()).isEqualTo(status);
        assertThat(report.getProcessedByAdminUserId()).isEqualTo(9L);
        assertThat(report.getProcessedAt()).isEqualTo(now.plusMinutes(1));
        assertThatThrownBy(() -> report.accept(10L, now.plusMinutes(2)))
                .isInstanceOf(CommunityException.class)
                .extracting("errorCode").isEqualTo(CommunityErrorCode.REPORT_ALREADY_PROCESSED);
        assertThatThrownBy(() -> report.decline(10L, now.plusMinutes(2)))
                .isInstanceOf(CommunityException.class);
        assertThat(report.getStatus()).isEqualTo(status);
        assertThat(report.getProcessedByAdminUserId()).isEqualTo(9L);
    }

    @Test
    void 잘못된_처리_정보는_상태를_변경하지_않는다() {
        CommunityReport report = CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "설명", now);
        assertThatThrownBy(() -> report.accept(null, now)).isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> report.decline(0L, now)).isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> report.accept(9L, null)).isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> report.decline(9L, now.minusSeconds(1))).isInstanceOf(CommunityException.class);
        assertThat(report.getStatus()).isEqualTo(CommunityReportStatus.PENDING);
        assertThat(report.getProcessedAt()).isNull();
        assertThat(report.getProcessedByAdminUserId()).isNull();
    }
}
