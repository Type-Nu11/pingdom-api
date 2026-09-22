package com.typenull.pingdom.identity.api.merchant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerPlaceManagementService;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.LocalDateTime;
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
class MerchantOwnerPlaceControllerTest {

    @Mock private MerchantOwnerPlaceManagementService service;

    private MockMvc mockMvc;

    /**
     * 미디어 요청의 검증 오류와 응답 매핑을 확인하도록 예외 처리기·Bean Validation·고정 점주 인자를 연결한 MockMvc를 만든다.
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantOwnerPlaceController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser가 붙은 컨트롤러 인자만 테스트용 인증 객체로 해석한다.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 서비스 위임 인자의 점주 식별자를 일정하게 검증하도록 사용자 20의 인증 객체를 제공한다.
                     */
                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(20L, "merchant");
                    }
                })
                .build();
    }

    /**
     * 발급된 S3 키와 순서로 미디어 생성을 요청하면 201과 서비스가 반환한 미디어 ID·키를 응답하는지 검증한다.
     */
    @Test
    void createsMerchantMedia() throws Exception {
        when(service.createMedia(20L, 10L, new MerchantOwnerMediaCreateRequest("places/10/exploration/20/new.jpg", 3)))
                .thenReturn(new PlaceMediaItem(
                        30L, 10L, PlaceMediaPurpose.EXPLORATION, "https://image",
                        "places/10/exploration/20/new.jpg", null, null, null,
                        3, LocalDateTime.of(2026, 8, 31, 12, 0), LocalDateTime.of(2026, 8, 31, 12, 0)
                ));

        mockMvc.perform(post("/merchant-owner/places/10/media")
                        .contentType("application/json")
                        .content("{\"s3Key\":\"places/10/exploration/20/new.jpg\",\"displayOrder\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.s3Key").value("places/10/exploration/20/new.jpg"));
    }

    /**
     * 빈 S3 키는 400과 필수 필드 메시지로 거절하며 미디어 서비스가 호출되지 않는지 검증한다.
     */
    @Test
    void rejectsBlankMediaKey() throws Exception {
        mockMvc.perform(post("/merchant-owner/places/10/media")
                        .contentType("application/json")
                        .content("{\"s3Key\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.s3Key").value("s3Key는 필수입니다."));

        verifyNoInteractions(service);
    }

    /**
     * 미디어 순서를 0으로 수정하는 요청이 서비스 응답의 변경 순서를 200 응답에 담는지 검증한다.
     */
    @Test
    void updatesMediaOrder() throws Exception {
        when(service.updateMediaOrder(20L, 10L, 30L, new MerchantOwnerMediaOrderUpdateRequest(0)))
                .thenReturn(new PlaceMediaItem(
                        30L, 10L, PlaceMediaPurpose.EXPLORATION, "https://image",
                        "places/10/exploration/20/new.jpg", null, null, null,
                        0, LocalDateTime.of(2026, 8, 31, 12, 0), LocalDateTime.of(2026, 8, 31, 12, 0)
                ));

        mockMvc.perform(patch("/merchant-owner/places/10/media/30")
                        .contentType("application/json")
                        .content("{\"displayOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayOrder").value(0));
    }

    /**
     * 음수 미디어 순서는 400과 최소값 검증 메시지로 거절하고 서비스를 호출하지 않는지 검증한다.
     */
    @Test
    void rejectsNegativeMediaOrder() throws Exception {
        mockMvc.perform(patch("/merchant-owner/places/10/media/30")
                        .contentType("application/json")
                        .content("{\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.displayOrder").value("노출 순서는 0 이상이어야 합니다."));

        verifyNoInteractions(service);
    }
}
