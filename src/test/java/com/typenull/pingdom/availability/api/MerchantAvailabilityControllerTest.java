package com.typenull.pingdom.availability.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.api.dto.ReservationTermsRequest;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class MerchantAvailabilityControllerTest {
    private static final String SLOTS_PATH = "/merchant-owner/availabilities";
    private static final String TERMS_PATH = SLOTS_PATH + "/9/reservation-terms";
    private static final String SLOT_BODY = """
            {"placeId":3,"startsAt":"2099-01-01T10:00:00","endsAt":"2099-01-01T11:00:00","totalCapacity":10}
            """;
    private static final String TERMS_BODY = """
            {"timezone":"Asia/Seoul","cancellable":true,"cancellationCutoffMinutes":60}
            """;
    private final PlaceAvailabilityService service = mock(PlaceAvailabilityService.class);
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private MockMvc mvc;

    /** Spring의 기본 JSON 설정과 공통 예외 처리기를 사용하며 보안 필터·DB 없이 요청 바인딩만 확인한다. */
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MerchantAvailabilityController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(7L, "owner");
                    }
                })
                .build();
    }

    /** GENERAL과 null을 포함한 폐기 필드가 슬롯 등록·수정에서 서비스 호출 전에 거절되는지 확인한다. */
    @ParameterizedTest
    @MethodSource("removedProductFields")
    void rejectsRemovedProductFields(HttpMethod method, String field, String value) throws Exception {
        String path = method == HttpMethod.POST ? SLOTS_PATH : SLOTS_PATH + "/9";
        mvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON)
                        .content(withField(SLOT_BODY, field, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_BODY.getCode()));
        verifyNoInteractions(service);
    }

    /** null인 금액·통화도 필드가 존재하면 거절해 구버전 유료 설정의 성공 오인을 방지한다. */
    @ParameterizedTest
    @MethodSource("removedPriceFields")
    void rejectsRemovedPriceFields(String field, String value) throws Exception {
        mvc.perform(put(TERMS_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(withField(TERMS_BODY, field, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_BODY.getCode()));
        verifyNoInteractions(service);
    }

    /** 삭제된 필드가 없는 현재 슬롯 본문은 등록·수정 서비스에 동일한 값으로 전달한다. */
    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void acceptsCurrentSlotRequests(String method) throws Exception {
        boolean create = "POST".equals(method);
        mvc.perform(request(HttpMethod.valueOf(method), create ? SLOTS_PATH : SLOTS_PATH + "/9")
                        .contentType(MediaType.APPLICATION_JSON).content(SLOT_BODY))
                .andExpect(status().is(create ? 201 : 200));
        var expected = new AvailabilityUpsertRequest(3L, LocalDateTime.of(2099, 1, 1, 10, 0),
                LocalDateTime.of(2099, 1, 1, 11, 0), 10);
        if (create) verify(service).create(7L, expected);
        else verify(service).update(7L, 9L, expected);
    }

    /** 취소 가능·불가 본문 모두 새 계약의 서비스 입력으로 정상 바인딩되는지 확인한다. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsCurrentCancellationTerms(boolean cancellable) throws Exception {
        String body = cancellable ? TERMS_BODY
                : "{\"timezone\":\"Asia/Seoul\",\"cancellable\":false,\"cancellationCutoffMinutes\":null}";
        mvc.perform(put(TERMS_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).updateReservationTerms(7L, 9L,
                new ReservationTermsRequest("Asia/Seoul", cancellable, cancellable ? 60 : null));
    }

    /** 폐기 목록에 없는 확장 필드를 무시하던 기존 동작까지 엄격하게 바꾸지 않는다. */
    @Test
    void retainsOtherUnknownFieldHandling() throws Exception {
        mvc.perform(post(SLOTS_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(withField(SLOT_BODY, "clientNote", "\"extension\"")))
                .andExpect(status().isCreated());
        mvc.perform(put(TERMS_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(withField(TERMS_BODY, "clientNote", "\"extension\"")))
                .andExpect(status().isOk());
        verify(service).create(eq(7L), any(AvailabilityUpsertRequest.class));
        verify(service).updateReservationTerms(eq(7L), eq(9L), any(ReservationTermsRequest.class));
    }

    private String withField(String body, String field, String value) throws Exception {
        var input = (ObjectNode) mapper.readTree(body);
        input.set(field, mapper.readTree(value));
        return mapper.writeValueAsString(input);
    }

    private static Stream<Arguments> removedProductFields() {
        return Stream.of(HttpMethod.POST, HttpMethod.PUT).flatMap(method -> Stream.of(
                Arguments.of(method, "productId", "31"), Arguments.of(method, "productId", "null"),
                Arguments.of(method, "productType", "\"TICKET\""), Arguments.of(method, "productType", "\"CLASS\""),
                Arguments.of(method, "productType", "\"GENERAL\""), Arguments.of(method, "productType", "null")));
    }

    private static Stream<Arguments> removedPriceFields() {
        return Stream.of(Arguments.of("unitAmountMinor", "1000"), Arguments.of("unitAmountMinor", "null"),
                Arguments.of("additionalAmountMinor", "50"), Arguments.of("additionalAmountMinor", "null"),
                Arguments.of("currency", "\"KRW\""), Arguments.of("currency", "null"));
    }
}
