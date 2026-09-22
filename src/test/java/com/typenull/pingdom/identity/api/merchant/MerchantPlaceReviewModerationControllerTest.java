package com.typenull.pingdom.identity.api.merchant;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewPageResponse;
import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewResponse;
import com.typenull.pingdom.place.application.service.review.MerchantPlaceReviewModerationService;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceReviewModerationControllerTest {

    @Mock
    private MerchantPlaceReviewModerationService reviewModerationService;

    private MockMvc mockMvc;

    /**
     * 리뷰 목록의 페이지 응답을 검증하도록 컨트롤러와 검증기·예외 처리기·고정 점주 인증 인자를 연결.
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantPlaceReviewModerationController(reviewModerationService))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser 어노테이션이 있는 메서드 인자에 테스트용 인증 해석기를 적용.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 리뷰 목록 서비스 호출의 사용자 ID를 검증할 수 있도록 점주 20을 반환.
                     */
                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return new JwtAuthenticatedUser(20L, "merchant");
                    }
                })
                .build();
    }

    /**
     * 점주 리뷰 목록 요청의 사용자·장소·페이지·크기를 서비스에 전달하는지 검증.
     * 페이지 메타데이터, 숨김 상태와 삭제 요청 null 값이 JSON 응답에 유지되는지도 확인.
     */
    @Test
    void returnsReviewPageWithNullableRequest() throws Exception {
        when(reviewModerationService.list(20L, 10L, 2, 10)).thenReturn(new MerchantPlaceReviewPageResponse(
                java.util.List.of(new MerchantPlaceReviewResponse(
                        100L,
                        10L,
                        30L,
                        "추천 이유",
                        java.util.List.of(),
                        "리뷰 내용",
                        java.util.List.of("https://example.com/review.jpg"),
                        java.util.List.of(),
                        java.time.LocalDateTime.of(2026, 8, 31, 10, 0),
                        PlaceReviewVisibilityStatus.HIDDEN,
                        null
                )),
                2,
                10,
                31,
                4,
                true
        ));

        mockMvc.perform(get("/merchant-owner/places/10/reviews").param("page", "2").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.totalElements").value(31))
                .andExpect(jsonPath("$.reviews[0].reviewId").value(100))
                .andExpect(jsonPath("$.reviews[0].visibilityStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.reviews[0].deletionRequest").value(nullValue()));

        verify(reviewModerationService).list(eq(20L), eq(10L), eq(2), eq(10));
    }
}
