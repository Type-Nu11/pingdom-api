package com.typenull.pingdom.identity.api.merchant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
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

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantOwnerPlaceController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(20L, "merchant");
                    }
                })
                .build();
    }

    @Test
    void createsMerchantMediaWithIssuedS3Key() throws Exception {
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

    @Test
    void rejectsBlankS3KeyBeforeCallingService() throws Exception {
        mockMvc.perform(post("/merchant-owner/places/10/media")
                        .contentType("application/json")
                        .content("{\"s3Key\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.s3Key").value("s3Key는 필수입니다."));

        verifyNoInteractions(service);
    }
}
