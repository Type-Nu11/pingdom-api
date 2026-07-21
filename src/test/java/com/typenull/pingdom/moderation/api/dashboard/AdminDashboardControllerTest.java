package com.typenull.pingdom.moderation.api.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentActivitiesResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentPlaceItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.query.dashboard.AdminDashboardQueryService;
import java.util.List;
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

    @Test
    void getRecentActivitiesReturnsDashboardActivities() throws Exception {
        when(adminDashboardQueryService.getRecentActivities(5))
                .thenReturn(new AdminDashboardRecentActivitiesResponse(
                        List.of(new AdminDashboardRecentPlaceItem(10L, "핑덤 카페", "서울특별시 중구 세종대로 110", 3L, "pingdom_user")),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        mockMvc.perform(get("/admin/dashboard/recent-activities").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].placeId").value(10))
                .andExpect(jsonPath("$.places[0].name").value("핑덤 카페"))
                .andExpect(jsonPath("$.posts.length()").value(0))
                .andExpect(jsonPath("$.reports.length()").value(0))
                .andExpect(jsonPath("$.userSanctions.length()").value(0));
    }

    @Test
    void getPendingItemsReturnsDashboardPendingItems() throws Exception {
        when(adminDashboardQueryService.getPendingItems(5))
                .thenReturn(new AdminDashboardPendingItemsResponse(
                        List.of(new AdminDashboardPendingItem(
                                "POST_REPORT",
                                30L,
                                "야경이 좋은 장소",
                                "PENDING",
                                java.time.LocalDateTime.of(2026, 7, 21, 15, 40)
                        ))
                ));

        mockMvc.perform(get("/admin/dashboard/pending-items").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("POST_REPORT"))
                .andExpect(jsonPath("$.items[0].targetId").value(30))
                .andExpect(jsonPath("$.items[0].title").value("야경이 좋은 장소"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }
}
