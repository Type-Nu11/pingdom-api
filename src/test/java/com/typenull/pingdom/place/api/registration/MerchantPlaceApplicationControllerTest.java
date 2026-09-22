package com.typenull.pingdom.place.api.registration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.place.api.dto.registration.NaverAddressSearchResponse;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationService;
import com.typenull.pingdom.place.application.service.registration.NaverAddressSearchService;
import com.typenull.pingdom.place.application.service.registration.NaverPlaceSearchService;
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
class MerchantPlaceApplicationControllerTest {

    @Mock
    private MerchantPlaceApplicationService applicationService;

    @Mock
    private NaverAddressSearchService naverAddressSearchService;

    @Mock
    private NaverPlaceSearchService naverPlaceSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantPlaceApplicationController(
                        applicationService, naverAddressSearchService, naverPlaceSearchService
                ))
                .setCustomArgumentResolvers(new CurrentUserResolver())
                .build();
    }

    /**
     * 인증된 신청 사용자가 주소 검색을 요청하면 후보 목록만 반환하고 장소 신청 생성이나 업체명 검색을 호출하지 않는지 검증한다.
     */
    @Test
    void returnsAddressResults() throws Exception {
        when(naverAddressSearchService.search("분당구 불정로 6")).thenReturn(new NaverAddressSearchResponse(List.of(
                new NaverAddressSearchResponse.Item("경기도 성남시 분당구 불정로 6", "정자동 178-1", "13561", 37.3595963, 127.1054328)
        )));

        mockMvc.perform(get("/users/me/merchant-place-applications/naver-address-search")
                        .param("query", "분당구 불정로 6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roadAddress").value("경기도 성남시 분당구 불정로 6"))
                .andExpect(jsonPath("$.items[0].postalCode").value("13561"))
                .andExpect(jsonPath("$.items[0].latitude").value(37.3595963))
                .andExpect(jsonPath("$.items[0].longitude").value(127.1054328));

        verify(naverAddressSearchService).search("분당구 불정로 6");
        verifyNoInteractions(applicationService, naverPlaceSearchService);
    }

    private static class CurrentUserResolver implements HandlerMethodArgumentResolver {
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
            return new JwtAuthenticatedUser(99L, "pending-merchant");
        }
    }
}
