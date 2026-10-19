package com.typenull.pingdom.place.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse.ReasonCount;
import com.typenull.pingdom.place.application.service.review.PlaceReviewSummaryService;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** standalone MVC는 응답·오류 변환을 검증하며 실제 보안 필터 실행은 포함하지 않습니다. */
class PlaceReviewSummaryControllerTest {

    private final PlaceReviewSummaryService service = mock(PlaceReviewSummaryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlaceReviewSummaryController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .build();
    }

    @Test
    void returnsCompleteSummaryWithoutRequestBody() throws Exception {
        when(service.summary(17L)).thenReturn(new PlaceReviewSummaryResponse(17, 3, 2,
                Arrays.stream(PlaceReviewRecommendReason.values()).map(reason -> new ReasonCount(reason, 0)).toList()));

        mvc.perform(get("/places/17/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(17))
                .andExpect(jsonPath("$.totalReviewCount").value(3))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.reasonCounts.length()").value(7))
                .andExpect(jsonPath("$.reasonCounts[0].reason").value("FRIENDLY"))
                .andExpect(jsonPath("$.reasonCounts[6].count").value(0));
        verify(service).summary(17L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "9223372036854775808"})
    void rejectsMalformedOrOverflowingPlaceId(String placeId) throws Exception {
        mvc.perform(get("/places/{placeId}/reviews/summary", placeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(longs = {404, 0, -1})
    void returnsPlaceNotFoundForMissingIds(long placeId) throws Exception {
        when(service.summary(placeId)).thenThrow(new MapException(MapErrorCode.PLACE_NOT_FOUND));

        mvc.perform(get("/places/{placeId}/reviews/summary", placeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void doesNotReplaceQueryFailureWithZeroCounts() throws Exception {
        when(service.summary(17L)).thenThrow(new DataAccessResourceFailureException("private database detail"));

        mvc.perform(get("/places/17/reviews/summary"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
