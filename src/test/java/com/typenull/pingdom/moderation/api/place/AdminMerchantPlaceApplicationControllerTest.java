package com.typenull.pingdom.moderation.api.place;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
class AdminMerchantPlaceApplicationControllerTest {

    @Mock private MerchantPlaceApplicationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminMerchantPlaceApplicationController(service))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer container,
                            NativeWebRequest request,
                            WebDataBinderFactory binderFactory
                    ) {
                        return new JwtAuthenticatedUser(99L, "admin");
                    }
                })
                .build();
    }

    @Test
    void listBindsRepeatedStatusesWithApplicationType() throws Exception {
        List<PlaceRegistrationStatus> statuses = List.of(
                PlaceRegistrationStatus.APPROVED,
                PlaceRegistrationStatus.COMPLETED,
                PlaceRegistrationStatus.REJECTED,
                PlaceRegistrationStatus.CANCELED
        );
        when(service.listForAdmin(
                99L,
                statuses,
                MerchantPlaceApplicationType.NEW_PLACE,
                2,
                10
        )).thenReturn(new AdminMerchantPlaceApplicationPageResponse(List.of(), 2, 10, 21, 3, true));

        mockMvc.perform(get("/admin/merchant-place-applications")
                        .param("status", "APPROVED", "COMPLETED", "REJECTED", "CANCELED")
                        .param("applicationType", "NEW_PLACE")
                        .param("page", "2")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(service).listForAdmin(
                eq(99L),
                eq(statuses),
                eq(MerchantPlaceApplicationType.NEW_PLACE),
                eq(2),
                eq(10)
        );
    }
}
