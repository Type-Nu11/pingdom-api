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

    /** 7번 사용자를 주입하는 resolver와 공통 예외 처리기로 리뷰 미디어 라우팅을 격리. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlaceReviewMediaController(mediaService))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /** CurrentUser 파라미터만 고정 인증 주체 주입 대상으로 선택. */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /** 보안 필터 대신 이 테스트에 고정된 JWT 사용자를 반환해 컨트롤러 전달 값을 검증. */
                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(7L, "reviewer");
                    }
                })
                .build();
    }

    /** 고정 인증 사용자·경로 장소·multipart 파일이 서비스에 전달되고 미디어 ID와 MIME이 201 응답에 포함되는지 확인. */
    @Test
    void uploadsAuthenticatedReviewMedia() throws Exception {
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

    /** 삭제 요청의 사용자·장소·미디어 ID를 취소 서비스에 전달하고 204를 반환하는지 확인. 실제 S3 삭제는 검증 범위에서 제외. */
    @Test
    void delegatesReviewMediaCancellation() throws Exception {
        mockMvc.perform(delete("/places/10/reviews/media/1"))
                .andExpect(status().isNoContent());

        verify(mediaService).cancel(7L, 10L, 1L);
    }
}
