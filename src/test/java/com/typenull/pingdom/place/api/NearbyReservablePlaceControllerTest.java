package com.typenull.pingdom.place.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.place.application.service.place.MapViewportQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceMediaService;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingNoticeService;
import com.typenull.pingdom.place.application.service.recommendation.explanation.PlaceRecommendationExplanationQueryService;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryService;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NearbyReservablePlaceControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PlaceController controller = new PlaceController(
                mock(PlaceQueryService.class),
                mock(PlaceRecommendationQueryService.class),
                mock(PlaceRecommendationClickService.class),
                mock(PlaceRecommendationExplanationQueryService.class),
                mock(PlaceMediaService.class),
                mock(PlaceOperatingNoticeService.class),
                mock(MapViewportQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .build();
    }

    @Test
    void missingCoordinatesReturnBadRequest() throws Exception {
        mockMvc.perform(get("/places/nearby-reservable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_PARAMETER.getCode()));
    }

    @Test
    void unsupportedProductTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/places/nearby-reservable")
                        .param("latitude", "35.8714")
                        .param("longitude", "128.6014")
                        .param("productType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_PARAMETER.getCode()));
    }
}
