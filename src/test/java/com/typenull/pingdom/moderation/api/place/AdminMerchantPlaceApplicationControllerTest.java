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

    /**
     * 관리자 신청 목록의 쿼리 인자 바인딩을 검증하도록 서비스와 고정 관리자 인증 인자를 연결한다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminMerchantPlaceApplicationController(service))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser 어노테이션을 가진 인자만 관리자 인증 객체로 해석한다.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 신청 목록 서비스에 전달할 심사 관리자 식별자를 99로 고정한다.
                     */
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

    /**
     * 반복 status 파라미터와 신청 유형·키워드·기간·페이지 조건이 정확한 타입과 값으로 서비스에 전달되는지 검증한다.
     * 응답의 전체 수·페이지 수·다음 페이지 여부도 확인한다.
     */
    @Test
    void bindsApplicationListFilters() throws Exception {
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
                "서울",
                java.time.LocalDateTime.parse("2026-09-01T00:00:00"),
                java.time.LocalDateTime.parse("2026-09-30T23:59:59"),
                2,
                10
        )).thenReturn(new AdminMerchantPlaceApplicationPageResponse(List.of(), 2, 10, 21, 3, true));

        mockMvc.perform(get("/admin/merchant-place-applications")
                        .param("status", "APPROVED", "COMPLETED", "REJECTED", "CANCELED")
                        .param("applicationType", "NEW_PLACE")
                        .param("keyword", "서울")
                        .param("submittedFrom", "2026-09-01T00:00:00")
                        .param("submittedTo", "2026-09-30T23:59:59")
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
                eq("서울"),
                eq(java.time.LocalDateTime.parse("2026-09-01T00:00:00")),
                eq(java.time.LocalDateTime.parse("2026-09-30T23:59:59")),
                eq(2),
                eq(10)
        );
    }
}
