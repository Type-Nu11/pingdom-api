package com.typenull.pingdom.product.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.product.application.ReservableProductService;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class MerchantReservableProductControllerTest {

    @Mock
    private ReservableProductService service;

    private MockMvc mockMvc;

    /**
     * 상품 서비스 mock과 공통 예외 처리기를 연결한 MockMvc를 구성.
     * 인증 사용자 해석을 고정해 요청 본문 검증에 집중.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantReservableProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler(org.mockito.Mockito.mock(AuthMetrics.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser annotation이 있는 인자만 테스트용 인증 사용자 해석 대상으로 선택.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 요청 인증 절차 대신 고정된 가맹점 사용자 ID 7을 반환.
                     */
                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return new JwtAuthenticatedUser(7L, "merchant");
                    }
                })
                .build();
    }

    /**
     * GENERAL 상품 등록 요청이 400과 INVALID_REQUEST_BODY 코드·메시지를 반환하는지 검증.
     * 서비스가 호출되지 않는지도 확인해 허용하지 않은 유형이 생성 로직에 도달하는 회귀를 방지.
     */
    @Test
    void rejectsGeneralProductType() throws Exception {
        mockMvc.perform(post("/merchant-owner/reservable-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeId": 3,
                                  "productType": "GENERAL",
                                  "name": "General reservation"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_BODY.getCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INVALID_REQUEST_BODY.getMessage()));

        verifyNoInteractions(service);
    }
}
