package com.typenull.pingdom.moderation.api.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.query.dashboard.AdminDashboardQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardQueryService adminDashboardQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminDashboardController(adminDashboardQueryService))
                .build();
    }

    @Test
    void getSummaryReturnsDashboardCounts() throws Exception {
        when(adminDashboardQueryService.getSummary())
                .thenReturn(new AdminDashboardSummaryResponse(44L, 58L, 5L, 6L));

        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(44))
                .andExpect(jsonPath("$.postCount").value(58))
                .andExpect(jsonPath("$.pendingReportCount").value(5))
                .andExpect(jsonPath("$.bannedUserCount").value(6));
    }
}
