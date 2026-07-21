package com.typenull.pingdom.moderation.api.trust;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreGrade;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreEvidenceResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreResponse;
import com.typenull.pingdom.moderation.application.query.trust.AdminTrustScoreQueryService;
import com.typenull.pingdom.moderation.application.service.trust.AdminTrustScoreService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminTrustScoreControllerTest {

    @Mock
    private AdminTrustScoreService adminTrustScoreService;

    @Mock
    private AdminTrustScoreQueryService adminTrustScoreQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminTrustScoreController(adminTrustScoreService, adminTrustScoreQueryService))
                .build();
    }

    @Test
    void getTrustScoreReturnsGradeAndEvidence() throws Exception {
        Long reporterUserId = 7L;
        LocalDateTime restrictedUntil = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(adminTrustScoreQueryService.getTrustScore(reporterUserId))
                .thenReturn(new AdminTrustScoreResponse(
                        reporterUserId,
                        "pingdom_user",
                        80,
                        TrustScoreGrade.HIGH,
                        true,
                        restrictedUntil,
                        "FALSE_REPORT_THRESHOLD_EXCEEDED",
                        new AdminTrustScoreEvidenceResponse(
                                12L,
                                8L,
                                4L,
                                3L,
                                66.67d,
                                100,
                                40L,
                                60L
                        )
                ));

        mockMvc.perform(get("/admin/trust-score/reporters/{reporterUserId}", reporterUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporterUserId").value(reporterUserId))
                .andExpect(jsonPath("$.reporterUsername").value("pingdom_user"))
                .andExpect(jsonPath("$.trustScore").value(80))
                .andExpect(jsonPath("$.trustGrade").value(TrustScoreGrade.HIGH.name()))
                .andExpect(jsonPath("$.restricted").value(true))
                .andExpect(jsonPath("$.restrictedUntil[0]").value(2026))
                .andExpect(jsonPath("$.restrictedUntil[1]").value(7))
                .andExpect(jsonPath("$.restrictedUntil[2]").value(27))
                .andExpect(jsonPath("$.restrictedUntil[3]").value(12))
                .andExpect(jsonPath("$.restrictedUntil[4]").value(0))
                .andExpect(jsonPath("$.restrictionReason").value("FALSE_REPORT_THRESHOLD_EXCEEDED"))
                .andExpect(jsonPath("$.evidence.submittedCount").value(12))
                .andExpect(jsonPath("$.evidence.acceptedCount").value(8))
                .andExpect(jsonPath("$.evidence.declinedCount").value(4))
                .andExpect(jsonPath("$.evidence.falseReportCount").value(3))
                .andExpect(jsonPath("$.evidence.acceptanceRate").value(66.67d))
                .andExpect(jsonPath("$.evidence.baseScore").value(100))
                .andExpect(jsonPath("$.evidence.acceptedScoreBonus").value(40))
                .andExpect(jsonPath("$.evidence.falseReportScorePenalty").value(60));
    }
}
