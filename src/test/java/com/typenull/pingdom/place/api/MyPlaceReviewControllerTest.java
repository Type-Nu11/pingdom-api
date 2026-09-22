package com.typenull.pingdom.place.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.place.api.dto.review.MyPlaceReviewPageResponse;
import com.typenull.pingdom.place.application.service.review.PlaceReviewService;
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
class MyPlaceReviewControllerTest {

    @Mock
    private PlaceReviewService placeReviewService;

    private MockMvc mockMvc;

    /** JWT 주체를 고정하는 argument resolver와 Bean Validation·공통 오류 처리기를 붙인 standalone MVC를 만든다. */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MyPlaceReviewController(placeReviewService))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /** CurrentUser 파라미터만 고정 인증 주체 주입 대상으로 선택한다. */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /** 보안 필터 대신 이 테스트에 고정된 JWT 사용자를 반환해 컨트롤러 전달 값을 검증한다. */
                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return new JwtAuthenticatedUser(20L, "reviewer");
                    }
                })
                .build();
    }

    /** 인증 주체를 20번 사용자로 주입하고 2페이지·10건 조회가 서비스에 전달되는지 확인한다. 응답의 전체 건수와 다음 페이지 여부도 유지한다. */
    @Test
    void returnsMyReviewPage() throws Exception {
        when(placeReviewService.listMine(20L, 2, 10))
                .thenReturn(new MyPlaceReviewPageResponse(java.util.List.of(), 2, 10, 31, 4, true));

        mockMvc.perform(get("/users/me/reviews").param("page", "2").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.totalElements").value(31))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(placeReviewService).listMine(eq(20L), eq(2), eq(10));
    }

}
