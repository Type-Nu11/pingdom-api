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

    /** 장소 서비스들을 mock으로 연결해 요청 바인딩과 오류 응답에 집중하는 standalone MVC를 구성한다. */
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

    /** 필수 좌표 없이 예약 가능 장소를 조회하면 400과 파라미터 오류 코드가 반환되는지 확인한다. */
    @Test
    void rejectsMissingCoordinates() throws Exception {
        mockMvc.perform(get("/places/nearby-reservable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_PARAMETER.getCode()));
    }

    /** 유효한 좌표와 정의되지 않은 상품 유형을 보내 enum 바인딩 실패가 400으로 변환되는지 확인한다. */
    @Test
    void rejectsUnknownProductType() throws Exception {
        mockMvc.perform(get("/places/nearby-reservable")
                        .param("latitude", "35.8714")
                        .param("longitude", "128.6014")
                        .param("productType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_PARAMETER.getCode()));
    }
}
