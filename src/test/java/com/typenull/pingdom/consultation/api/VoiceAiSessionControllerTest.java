package com.typenull.pingdom.consultation.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.consultation.api.dto.VoiceAiSessionResponse;
import com.typenull.pingdom.consultation.application.VoiceAiSessionService;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class VoiceAiSessionControllerTest {
    private final VoiceAiSessionService service = mock(VoiceAiSessionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new VoiceAiSessionController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder().addModule(new JavaTimeModule()).build()))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == JwtAuthenticatedUser.class; }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) { return new JwtAuthenticatedUser(1L, "voice"); }
                }).build();
    }

    @Test
    void sessionResponseHasOffsetAndRequiredFields() throws Exception {
        when(service.create(1L)).thenReturn(new VoiceAiSessionResponse("session", OffsetDateTime.parse("2026-09-17T12:05:00+09:00")));
        mvc.perform(post("/voice-ai/sessions")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-17T12:05:00+09:00"));
    }

    @ParameterizedTest
    @EnumSource(VoiceAiErrorCode.class)
    void domainErrorsPreserveStatusAndCode(VoiceAiErrorCode code) throws Exception {
        when(service.send("session", 1L, "hello", "r1")).thenThrow(new VoiceAiException(code));
        mvc.perform(post("/voice-ai/sessions/session/messages").contentType("application/json")
                        .content("{\"text\":\"hello\",\"requestId\":\"r1\"}"))
                .andExpect(status().is(code.getStatus().value())).andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void invalidRequestUsesValidationSchema() throws Exception {
        mvc.perform(post("/voice-ai/sessions/session/messages").contentType("application/json")
                        .content("{\"text\":\"\",\"requestId\":\"invalid id\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.text").isString());
        verifyNoInteractions(service);
    }
}
