package com.typenull.pingdom.moderation.application.query.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreGrade;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTrustScoreQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T12:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private ReporterModerationPolicyRepository reporterModerationPolicyRepository;

    private AdminTrustScoreQueryService service;

    /**
     * 신고 제한의 현재 유효 여부를 판정할 서울 시간대 고정 Clock으로 신뢰도 조회 서비스를 만든다.
     */
    @BeforeEach
    void setUp() {
        service = new AdminTrustScoreQueryService(reporterModerationPolicyRepository, FIXED_CLOCK);
    }

    /**
     * 신고 정책을 조회하면 신고자 정보·점수 80·HIGH 등급과 접수·수락·기각·허위 건수를 응답하는지 검증한다.
     * 수락률 66.67%, 기본점수·가산·감점 근거와 비제한 상태도 확인한다.
     */
    @Test
    void returnsTrustGradeAndEvidence() {
        Long reporterUserId = 7L;
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(reporterUserId)
                .reporterUsername("pingdom_user")
                .submittedCount(12L)
                .acceptedCount(8L)
                .declinedCount(4L)
                .falseReportCount(3L)
                .trustScore(80)
                .build();
        when(reporterModerationPolicyRepository.findById(reporterUserId)).thenReturn(Optional.of(policy));

        AdminTrustScoreResponse response = service.getTrustScore(reporterUserId);

        assertEquals(reporterUserId, response.reporterUserId());
        assertEquals("pingdom_user", response.reporterUsername());
        assertEquals(80, response.trustScore());
        assertEquals(TrustScoreGrade.HIGH, response.trustGrade());
        assertFalse(response.restricted());
        assertEquals(12L, response.evidence().submittedCount());
        assertEquals(8L, response.evidence().acceptedCount());
        assertEquals(4L, response.evidence().declinedCount());
        assertEquals(3L, response.evidence().falseReportCount());
        assertEquals(66.67d, response.evidence().acceptanceRate());
        assertEquals(100, response.evidence().baseScore());
        assertEquals(40L, response.evidence().acceptedScoreBonus());
        assertEquals(60L, response.evidence().falseReportScorePenalty());
    }

    /**
     * 낮은 점수와 미래 제한 기한이 있는 신고자는 LOW 등급, 제한 true, 기한과 사유를 응답하는지 검증한다.
     */
    @Test
    void returnsActiveTrustRestriction() {
        Long reporterUserId = 7L;
        LocalDateTime restrictedUntil = LocalDateTime.of(2026, 7, 21, 21, 0);
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(reporterUserId)
                .reporterUsername("restricted_user")
                .submittedCount(3L)
                .acceptedCount(0L)
                .declinedCount(3L)
                .falseReportCount(3L)
                .trustScore(40)
                .restrictedUntil(restrictedUntil)
                .restrictionReason("FALSE_REPORT_THRESHOLD_EXCEEDED")
                .build();
        when(reporterModerationPolicyRepository.findById(reporterUserId)).thenReturn(Optional.of(policy));

        AdminTrustScoreResponse response = service.getTrustScore(reporterUserId);

        assertEquals(TrustScoreGrade.LOW, response.trustGrade());
        assertTrue(response.restricted());
        assertEquals(restrictedUntil, response.restrictedUntil());
        assertEquals("FALSE_REPORT_THRESHOLD_EXCEEDED", response.restrictionReason());
    }

    /**
     * 신고자 정책이 없으면 TRUST_SCORE_REPORTER_POLICY_NOT_FOUND 오류를 반환하는지 검증한다.
     */
    @Test
    void rejectsMissingTrustPolicy() {
        Long reporterUserId = 7L;
        when(reporterModerationPolicyRepository.findById(reporterUserId)).thenReturn(Optional.empty());

        AdminException exception = assertThrows(AdminException.class, () -> service.getTrustScore(reporterUserId));

        assertEquals(AdminErrorCode.TRUST_SCORE_REPORTER_POLICY_NOT_FOUND, exception.getErrorCode());
    }
}
