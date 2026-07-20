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

    @BeforeEach
    void setUp() {
        service = new AdminTrustScoreQueryService(reporterModerationPolicyRepository, FIXED_CLOCK);
    }

    @Test
    void getTrustScoreReturnsGradeAndEvidence() {
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

    @Test
    void getTrustScoreReturnsRestrictedState() {
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

    @Test
    void getTrustScoreThrowsWhenPolicyNotFound() {
        Long reporterUserId = 7L;
        when(reporterModerationPolicyRepository.findById(reporterUserId)).thenReturn(Optional.empty());

        AdminException exception = assertThrows(AdminException.class, () -> service.getTrustScore(reporterUserId));

        assertEquals(AdminErrorCode.TRUST_SCORE_REPORTER_POLICY_NOT_FOUND, exception.getErrorCode());
    }
}
