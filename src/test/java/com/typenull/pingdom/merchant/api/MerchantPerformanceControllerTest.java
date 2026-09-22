package com.typenull.pingdom.merchant.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.merchant.api.dto.MerchantPerformanceResponse;
import com.typenull.pingdom.merchant.application.MerchantPerformanceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;

@ExtendWith(MockitoExtension.class)
class MerchantPerformanceControllerTest {
    @Mock
    private MerchantPerformanceQueryService queryService;

    private MockMvc mockMvc;

    /**
     * 사용자 인자를 점주 7로 고정한 MockMvc를 구성해 성과 응답의 JSON 매핑을 검증.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantPerformanceController(queryService))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser가 선언된 컨트롤러 인자만 테스트 인증 사용자 해석 대상으로 선택.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 실제 인증 필터 대신 고정된 점주 ID 7을 반환해 성과 조회 호출 대상을 지정.
                     */
                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(7L, "merchant");
                    }
                })
                .build();
    }

    /**
     * 점주 성과 요청이 200과 장소 수·노출 수·클릭 수·예약 전환율을 지정된 값으로 반환하는지 검증.
     */
    @Test
    void returnsMerchantPerformanceSummary() throws Exception {
        when(queryService.get(7L)).thenReturn(new MerchantPerformanceResponse(
                2, 1_000, 200, 50, 40, 30, 20.0, 15.0));

        mockMvc.perform(get("/merchant-owner/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.exposureCount").value(1_000))
                .andExpect(jsonPath("$.clickCount").value(200))
                .andExpect(jsonPath("$.reservationConversionRate").value(15.0));
    }
}
