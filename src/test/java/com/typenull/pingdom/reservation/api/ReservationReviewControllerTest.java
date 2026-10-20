package com.typenull.pingdom.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 보안 필터와 DB 없이 HTTP 매핑·입력 검증·서비스 전달을 확인한다. */
class ReservationReviewControllerTest {
    private final ReservationService service = mock(ReservationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MerchantReservationController(service),
                        new AdminReservationController(service))
                .setControllerAdvice(new ReservationAccessDeniedAdvice(), new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(8L, "owner");
                    }
                }).build();
    }

    @Test
    void methodAuthorizationFailureKeepsReservationForbiddenResponse() throws Exception {
        when(service.confirmOwned(8L, 3L, null))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        mvc.perform(post("/merchant-owner/reservations/3/confirm"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESERVATION_FORBIDDEN"));
    }

    @Test
    void approvesWithoutOptionalBody() throws Exception {
        mvc.perform(post("/merchant-owner/reservations/3/confirm")).andExpect(status().isOk());
        verify(service).confirmOwned(8L, 3L, null);
    }

    @Test
    void approvesWithReason() throws Exception {
        mvc.perform(post("/merchant-owner/reservations/3/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"확인 완료\"}")).andExpect(status().isOk());
        verify(service).confirmOwned(8L, 3L, "확인 완료");
    }

    @Test
    void rejectsWithRequiredReason() throws Exception {
        mvc.perform(post("/merchant-owner/reservations/3/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"영업 일정 변경\"}")).andExpect(status().isOk());
        verify(service).rejectOwned(8L, 3L, "영업 일정 변경");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"  \"}"})
    void rejectsInvalidRejectionBody(String body) throws Exception {
        mvc.perform(post("/merchant-owner/reservations/3/reject").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingRejectionBody() throws Exception {
        mvc.perform(post("/merchant-owner/reservations/3/reject")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "reject"})
    void rejectsOversizedReviewReason(String action) throws Exception {
        mvc.perform(post("/merchant-owner/reservations/3/" + action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "가".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void forwardsOwnedListFiltersWithAuthenticatedOwner() throws Exception {
        mvc.perform(get("/merchant-owner/reservations").param("status", "PENDING").param("placeId", "11")
                .param("reservationFrom", "2026-10-20T00:00:00").param("reservationTo", "2026-10-21T00:00:00"))
                .andExpect(status().isOk());
        verify(service).listOwned(8L, ReservationStatus.PENDING, 11L, LocalDateTime.of(2026, 10, 20, 0, 0),
                LocalDateTime.of(2026, 10, 21, 0, 0), 1, 20);
    }

    @Test
    void getsOwnedDetail() throws Exception {
        mvc.perform(get("/merchant-owner/reservations/3")).andExpect(status().isOk());
        verify(service).getOwned(8L, 3L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "reject", "cancel"})
    void adminMutationRoutesDoNotExist(String action) throws Exception {
        mvc.perform(post("/admin/reservations/3/" + action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"시도\"}"))
                .andExpect(result -> assertThat(result.getHandler()).isNull())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.web.servlet.NoHandlerFoundException.class));
        verifyNoInteractions(service);
    }

    @Test
    void adminRetainsListAndDetailReads() throws Exception {
        mvc.perform(get("/admin/reservations")).andExpect(status().isOk());
        mvc.perform(get("/admin/reservations/3")).andExpect(status().isOk());
        verify(service).listForAdmin(null, null, null, null, null, null, 1, 20);
        verify(service).getForAdmin(3L);
    }
}
