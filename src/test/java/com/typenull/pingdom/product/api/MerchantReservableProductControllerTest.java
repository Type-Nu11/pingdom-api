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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantReservableProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler(org.mockito.Mockito.mock(AuthMetrics.class)))
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
                        return new JwtAuthenticatedUser(7L, "merchant");
                    }
                })
                .build();
    }

    @Test
    void rejectsGeneralProductTypeAsInvalidRequestBody() throws Exception {
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
