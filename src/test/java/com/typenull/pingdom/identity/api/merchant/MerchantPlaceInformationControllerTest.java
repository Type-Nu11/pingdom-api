package com.typenull.pingdom.identity.api.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceInformationService;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import java.time.LocalDateTime;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
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
import com.typenull.pingdom.shared.security.annotation.CurrentUser;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceInformationControllerTest {

    @Mock private MerchantPlaceInformationService informationService;

    private MockMvc mockMvc;

    /**
     * 장소 정보 API의 검증 및 오류 응답을 확인하도록 Bean Validation과 예외 처리기, 고정 점주 인자를 설정한다.
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantPlaceInformationController(informationService))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setValidator(validator)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser 어노테이션을 가진 인자에만 테스트용 인증 해석기를 적용한다.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 장소 정보 서비스에 전달할 점주 사용자 식별자를 20으로 고정한다.
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
     * 장소 정보 GET은 장소 ID·소개를, PUT은 수정자 ID를 서비스 응답에서 200 JSON 응답으로 옮기는지 검증한다.
     */
    @Test
    void exposesPlaceInformationEndpoints() throws Exception {
        MerchantPlaceInformationResponse response = new MerchantPlaceInformationResponse(
                10L,
                "K-컬처 체험 공간",
                "010-1234-5678",
                "https://example.com/place",
                "https://example.com/reserve",
                20L,
                LocalDateTime.of(2026, 8, 5, 12, 0),
                LocalDateTime.of(2026, 8, 5, 13, 0)
        );
        when(informationService.get(20L, 10L)).thenReturn(response);
        when(informationService.upsert(any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/merchant-owner/places/10/information"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(10))
                .andExpect(jsonPath("$.description").value("K-컬처 체험 공간"));

        mockMvc.perform(put("/merchant-owner/places/10/information")
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "K-컬처 체험 공간",
                                  "contactPhone": "010-1234-5678",
                                  "websiteUrl": "https://example.com/place",
                                  "reservationUrl": "https://example.com/reserve"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedByUserId").value(20));
    }

    /**
     * 최대 허용 길이인 1,000자 소개를 수정 요청하면 200과 전체 소개 문자열을 반환하는지 검증한다.
     */
    @Test
    void acceptsMaximumDescriptionLength() throws Exception {
        MerchantPlaceInformationResponse response = new MerchantPlaceInformationResponse(
                10L,
                "a".repeat(1000),
                null,
                null,
                null,
                20L,
                LocalDateTime.of(2026, 8, 5, 12, 0),
                LocalDateTime.of(2026, 8, 5, 13, 0)
        );
        when(informationService.upsert(any(), any(), any())).thenReturn(response);

        mockMvc.perform(put("/merchant-owner/places/10/information")
                        .contentType("application/json")
                        .content("{\"description\":\"" + "a".repeat(1000) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("a".repeat(1000)));
    }

    /**
     * 프로토콜 없는 웹사이트 URL은 400과 필드 오류 메시지로 거절하며 서비스를 호출하지 않는지 검증한다.
     */
    @Test
    void rejectsInvalidWebsiteUrl() throws Exception {
        mockMvc.perform(put("/merchant-owner/places/10/information")
                        .contentType("application/json")
                        .content("{\"websiteUrl\":\"example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors.websiteUrl").value("웹사이트 URL은 http:// 또는 https://로 시작해야 합니다."));

        verifyNoInteractions(informationService);
    }

    /**
     * 서비스의 장소 정보 없음 오류가 HTTP 404와 PLACE_INFORMATION_NOT_FOUND 코드로 노출되는지 검증한다.
     */
    @Test
    void mapsMissingInformationError() throws Exception {
        when(informationService.get(20L, 10L))
                .thenThrow(new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_INFORMATION_NOT_FOUND));

        mockMvc.perform(get("/merchant-owner/places/10/information"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_INFORMATION_NOT_FOUND"));
    }

    /**
     * 서비스의 관리자 팀 권한 부족 오류가 HTTP 403과 해당 도메인 오류 코드로 노출되는지 검증한다.
     */
    @Test
    void mapsManagerPermissionError() throws Exception {
        when(informationService.get(20L, 10L))
                .thenThrow(new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED));

        mockMvc.perform(get("/merchant-owner/places/10/information"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_TEAM_PERMISSION_REQUIRED"));
    }
}
