package com.typenull.pingdom.place.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationExposureEventListenerTest {

    @Mock
    private PlaceRecommendationExposureService placeRecommendationExposureService;

    private PlaceRecommendationExposureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService,
                task -> task.run()
        );
    }

    @Test
    @DisplayName("추천 노출 기록은 커밋 후에 별도 처리로 위임한다")
    void handlesExposureAfterCommit() throws NoSuchMethodException {
        Method handleMethod = PlaceRecommendationExposureEventListener.class.getDeclaredMethod(
                "handle",
                PlaceRecommendationExposureRecordRequestedEvent.class
        );
        TransactionalEventListener annotation = handleMethod.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();

        PlaceRecommendationExposureRecordRequestedEvent event = event();
        eventListener.handle(event);

        verify(placeRecommendationExposureService).recordExposures(
                7L,
                35.1800d,
                128.1070d,
                "recommendation-request-id",
                List.of(101L, 102L),
                "place-rec-v1"
        );
    }

    @Test
    @DisplayName("추천 노출 기록 실패는 이미 성공한 추천 응답 흐름으로 전파하지 않는다")
    void suppressesExposurePersistenceFailure() {
        PlaceRecommendationExposureRecordRequestedEvent event = event();
        doThrow(new IllegalStateException("exposure persistence failed"))
                .when(placeRecommendationExposureService)
                .recordExposures(
                        event.userId(),
                        event.latitude(),
                        event.longitude(),
                        event.requestId(),
                        event.placeIds(),
                        event.recommendationVersion()
                );

        assertThatCode(() -> eventListener.handle(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("추천 노출 작업 제출 실패는 추천 응답 흐름으로 전파하지 않는다")
    void suppressesExecutorSubmissionFailure() {
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService,
                task -> {
                    throw new RejectedExecutionException("executor is unavailable");
                }
        );

        assertThatCode(() -> eventListener.handle(event())).doesNotThrowAnyException();
    }

    private PlaceRecommendationExposureRecordRequestedEvent event() {
        return new PlaceRecommendationExposureRecordRequestedEvent(
                7L,
                35.1800d,
                128.1070d,
                "recommendation-request-id",
                List.of(101L, 102L),
                "place-rec-v1"
        );
    }
}
