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

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantPlaceReviewModerationController(reviewModerationService))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

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

    @Test
    void returnsMerchantReviewPageWithNullableDeletionRequest() throws Exception {
        when(reviewModerationService.list(20L, 10L, 2, 10)).thenReturn(new MerchantPlaceReviewPageResponse(
                java.util.List.of(new MerchantPlaceReviewResponse(
                        100L,
                        10L,
                        30L,
                        "추천 이유",
                        "리뷰 내용",
                        java.util.List.of("https://example.com/review.jpg"),
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
