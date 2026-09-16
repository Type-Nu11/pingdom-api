package com.typenull.pingdom.place.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewMediaUploadResponse;
import com.typenull.pingdom.place.application.service.review.PlaceReviewMediaService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class PlaceReviewMediaControllerTest {

    @Mock
    private PlaceReviewMediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlaceReviewMediaController(mediaService))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(7L, "reviewer");
                    }
                })
                .build();
    }

    @Test
    void uploadsReviewMediaWithAuthenticatedUserAndPlace() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "review.png", "image/png", new byte[] {1, 2, 3});
        when(mediaService.upload(eq(7L), eq(10L), eq(file)))
                .thenReturn(new PlaceReviewMediaUploadResponse(1L, "https://cdn.test/review.png", "image/png", 3,
                        LocalDateTime.of(2026, 9, 17, 1, 0)));

        mockMvc.perform(multipart("/places/10/reviews/media").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewMediaId").value(1))
                .andExpect(jsonPath("$.contentType").value("image/png"));

        verify(mediaService).upload(7L, 10L, file);
    }

    @Test
    void cancelsOnlyTheSpecifiedReviewMediaUpload() throws Exception {
        mockMvc.perform(delete("/places/10/reviews/media/1"))
                .andExpect(status().isNoContent());

        verify(mediaService).cancel(7L, 10L, 1L);
    }
}
