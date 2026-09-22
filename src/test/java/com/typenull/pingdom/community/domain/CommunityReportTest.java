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

    /**
     * 모든 신고 사유로 게시글 신고를 만들면 대상·신고자·사유를 보존하고 설명 양끝 공백을 제거하는지 검증한다.
     * 초기 상태가 PENDING이며 처리자·처리 시각이 비어 있는지도 확인한다.
     */
    @ParameterizedTest
    @EnumSource(CommunityReportReason.class)
    void acceptsEveryPostReportReason(CommunityReportReason reason) {
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

    /**
     * 댓글 신고는 댓글 참조만 보존하고 게시글 대상 참조는 null로 두는지 검증한다.
     */
    @Test
    void referencesOnlyReportedComment() {
        CommunityPostComment comment = CommunityPostComment.create(post, 3L, "댓글");
        CommunityReport report = CommunityReport.reportComment(2L, comment, CommunityReportReason.SPAM, "설명", now);

        assertThat(report.getComment()).isSameAs(comment);
        assertThat(report.getPost()).isNull();
    }

    /**
     * null·빈 문자열·일반 공백·줄바꿈·전각 공백뿐인 신고 설명을 INVALID_REPORT로 거절하는지 검증한다.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void rejectsBlankReportDescription(String description) {
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, post, CommunityReportReason.OTHER, description, now))
                .isInstanceOf(CommunityException.class)
                .extracting("errorCode").isEqualTo(CommunityErrorCode.INVALID_REPORT);
    }

    /**
     * 신고 설명은 500자를 허용하고 501자는 도메인 예외로 거절하는지 경계를 검증한다.
     */
    @Test
    void checksReportDescriptionLength() {
        assertThat(CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "가".repeat(500), now)
                .getDescription()).hasSize(500);
        assertThatThrownBy(() -> CommunityReport.reportPost(2L, post, CommunityReportReason.SPAM, "가".repeat(501), now))
                .isInstanceOf(CommunityException.class);
    }

    /**
     * 신고자가 없거나 양수가 아니고, 대상·사유·접수 시각 중 필수값이 없으면 신고 생성을 거절하는지 검증한다.
     */
    @Test
    void requiresReportCreationFields() {
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

    /**
     * 승인 또는 거절하면 상태·처리자·처리 시각을 기록하고 이후 승인·거절 요청을 모두 막는지 검증한다.
     * 재처리 실패 뒤에도 최초 상태와 처리자가 유지되는지 확인한다.
     */
    @ParameterizedTest
    @EnumSource(value = CommunityReportStatus.class, names = {"ACCEPTED", "DECLINED"})
    void preventsProcessedReportTransitions(CommunityReportStatus status) {
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

    /**
     * 유효하지 않은 관리자 ID나 처리 시각으로 심사하면 예외를 반환하고 대기 상태와 비어 있는 처리 메타데이터를 유지하는지 검증한다.
     */
    @Test
    void preservesPendingOnInvalidProcessing() {
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
