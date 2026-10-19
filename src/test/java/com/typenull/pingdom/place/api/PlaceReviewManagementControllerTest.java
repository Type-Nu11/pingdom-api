package com.typenull.pingdom.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.typenull.pingdom.place.api.dto.review.*;
import com.typenull.pingdom.place.application.service.review.PlaceReviewService;
import com.typenull.pingdom.shared.exception.*;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

class PlaceReviewManagementControllerTest {
    private final PlaceReviewService service = mock(PlaceReviewService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlaceReviewController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) { return parameter.hasParameterAnnotation(CurrentUser.class); }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request, WebDataBinderFactory factory) { return new JwtAuthenticatedUser(7L, "reviewer"); }
                }).build();
    }

    @Test
    void delegatesPartialUpdateAndReturnsExistingReviewContract() throws Exception {
        when(service.update(eq(7L), eq(10L), eq(1L), any())).thenReturn(new PlaceReviewResponse(1L, 10L, 7L, "FRIENDLY", List.of(), "수정", List.of(), List.of(), null));
        mvc.perform(patch("/places/10/reviews/1").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정\",\"reviewMediaIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewId").value(1)).andExpect(jsonPath("$.content").value("수정"));
        var captor = org.mockito.ArgumentCaptor.forClass(PlaceReviewUpdateRequest.class);
        verify(service).update(eq(7L), eq(10L), eq(1L), captor.capture());
        assertThat(captor.getValue().reviewMediaIds()).isEmpty();
        assertThat(captor.getValue().recommendReasons()).isNull();
    }

    @Test
    void rejectsDuplicateReasonsNullPhotoIdAndBlankContentBeforeService() throws Exception {
        for (String body : List.of("{\"recommendReasons\":[\"CLEAN\",\"CLEAN\"]}", "{\"reviewMediaIds\":[null]}", "{\"content\":\"  \"}", "{\"recommendReason\":\"old\",\"recommendReasons\":[\"CLEAN\"]}")) {
            mvc.perform(patch("/places/10/reviews/1").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void deletesOwnReviewAndMapsMissingReview() throws Exception {
        mvc.perform(delete("/places/10/reviews/1")).andExpect(status().isNoContent());
        verify(service).delete(7L, 10L, 1L);
        doThrow(new MapException(MapErrorCode.PLACE_REVIEW_NOT_FOUND)).when(service).delete(7L, 10L, 2L);
        mvc.perform(delete("/places/10/reviews/2")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLACE_REVIEW_NOT_FOUND"));
    }
}
