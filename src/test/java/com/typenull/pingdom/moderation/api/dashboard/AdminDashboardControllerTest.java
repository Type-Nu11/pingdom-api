package com.typenull.pingdom.moderation.api.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardMetricWindowResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardOperationalMetricsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentActivitiesResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentPlaceItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.query.dashboard.AdminDashboardQueryService;
import java.time.LocalDateTime;
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
                .thenReturn(new AdminDashboardSummaryResponse(
                        44L,
                        58L,
                        5L,
                        6L,
                        operationalMetrics()
                ));

        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(44))
                .andExpect(jsonPath("$.postCount").value(58))
                .andExpect(jsonPath("$.pendingReportCount").value(5))
                .andExpect(jsonPath("$.bannedUserCount").value(6))
                .andExpect(jsonPath("$.operationalMetrics.today.placeRegistrationCount").value(3))
                .andExpect(jsonPath("$.operationalMetrics.today.postRegistrationCount").value(7))
                .andExpect(jsonPath("$.operationalMetrics.last7Days.placeRegistrationCount").value(12))
                .andExpect(jsonPath("$.operationalMetrics.last7Days.postRegistrationCount").value(31))
                .andExpect(jsonPath("$.operationalMetrics.duplicatePlaceGroupCount").value(2))
                .andExpect(jsonPath("$.operationalMetrics.expiringBannedUserCount").value(4))
                .andExpect(jsonPath("$.operationalMetrics.missingLocationPlaceCount").value(1));
    }

    @Test
    void getRecentActivitiesReturnsDashboardActivities() throws Exception {
        when(adminDashboardQueryService.getRecentActivities(5))
                .thenReturn(new AdminDashboardRecentActivitiesResponse(
                        List.of(new AdminDashboardRecentPlaceItem(
                                10L,
                                "핑덤 카페",
                                "서울특별시 중구 세종대로 110",
                                3L,
                                "pingdom_user",
                                LocalDateTime.of(2026, 7, 21, 15, 30)
                        )),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        mockMvc.perform(get("/admin/dashboard/recent-activities").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].placeId").value(10))
                .andExpect(jsonPath("$.places[0].name").value("핑덤 카페"))
                .andExpect(jsonPath("$.places[0].createdAt").isArray())
                .andExpect(jsonPath("$.places[0].createdAt[0]").value(2026))
                .andExpect(jsonPath("$.places[0].createdAt[1]").value(7))
                .andExpect(jsonPath("$.places[0].createdAt[2]").value(21))
                .andExpect(jsonPath("$.places[0].createdAt[3]").value(15))
                .andExpect(jsonPath("$.places[0].createdAt[4]").value(30))
                .andExpect(jsonPath("$.posts.length()").value(0))
                .andExpect(jsonPath("$.reports.length()").value(0))
                .andExpect(jsonPath("$.userSanctions.length()").value(0));
    }

    @Test
    void getPendingItemsReturnsDashboardPendingItems() throws Exception {
        when(adminDashboardQueryService.getPendingItems(5))
                .thenReturn(new AdminDashboardPendingItemsResponse(
                        List.of(new AdminDashboardPendingItem(
                                com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemType.POST_REPORT,
                                30L,
                                30L,
                                22L,
                                "야경이 좋은 장소",
                                "PENDING",
                                java.time.LocalDateTime.of(2026, 7, 21, 15, 40),
                                null
                        )),
                        1L
                ));

        mockMvc.perform(get("/admin/dashboard/pending-items").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("POST_REPORT"))
                .andExpect(jsonPath("$.items[0].targetId").value(30))
                .andExpect(jsonPath("$.items[0].reportId").value(30))
                .andExpect(jsonPath("$.items[0].postId").value(22))
                .andExpect(jsonPath("$.items[0].title").value("야경이 좋은 장소"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void getPendingItemsReturnsEmptyArrayWhenNothingNeedsProcessing() throws Exception {
        when(adminDashboardQueryService.getPendingItems(5))
                .thenReturn(new AdminDashboardPendingItemsResponse(List.of(), 0L));

        mockMvc.perform(get("/admin/dashboard/pending-items").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private AdminDashboardOperationalMetricsResponse operationalMetrics() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 15, 30);
        return new AdminDashboardOperationalMetricsResponse(
                new AdminDashboardMetricWindowResponse(
                        "TODAY",
                        LocalDateTime.of(2026, 7, 21, 0, 0),
                        now,
                        3L,
                        7L
                ),
                new AdminDashboardMetricWindowResponse(
                        "LAST_7_DAYS",
                        LocalDateTime.of(2026, 7, 15, 0, 0),
                        now,
                        12L,
                        31L
                ),
                2L,
                4L,
                1L,
                LocalDateTime.of(2026, 7, 28, 15, 30),
                now
        );
    }
}
