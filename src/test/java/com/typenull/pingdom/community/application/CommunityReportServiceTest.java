package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.community.api.dto.CommunityReportCreateRequest;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityReportReason;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CommunityReportServiceTest {
    @Mock private CommunityPostRepository posts;
    @Mock private CommunityPostCommentRepository comments;
    @Mock private CommunityReportRepository reports;
    private CommunityReportService service;
    private final CommunityReportCreateRequest request = new CommunityReportCreateRequest(CommunityReportReason.SPAM, "설명");

    @BeforeEach
    void setup() {
        service = new CommunityReportService(posts, comments, reports, Clock.systemUTC());
        when(posts.findByIdAndHiddenFalse(1L))
                .thenReturn(Optional.of(CommunityPost.create("TRAVEL", "제목", "내용", 1L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk_community_report_reporter_post", "uk_community_report_reporter_comment"})
    void 사전_조회_이후_중복_충돌도_도메인_오류로_변환한다(String constraint) {
        when(reports.saveAndFlush(any())).thenThrow(failure(constraint));
        assertThatThrownBy(() -> service.reportPost(1L, 2L, request))
                .isInstanceOf(CommunityException.class).extracting("errorCode").isEqualTo(CommunityErrorCode.ALREADY_REPORTED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"fk_community_report_post", "ck_community_report_target"})
    void 다른_DB_오류를_중복_신고로_오인하지_않는다(String constraint) {
        DataIntegrityViolationException failure = failure(constraint);
        when(reports.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.reportPost(1L, 2L, request)).isSameAs(failure);
    }

    private DataIntegrityViolationException failure(String constraint) {
        return new DataIntegrityViolationException("constraint", new ConstraintViolationException("constraint", new SQLException(), constraint));
    }
}
