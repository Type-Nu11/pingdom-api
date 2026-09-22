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

    /** executor를 동기 실행으로 바꿔 서비스 위임과 예외 억제를 테스트 호출 안에서 관찰. */
    @BeforeEach
    void setUp() {
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService,
                task -> task.run()
        );
    }

    /** AFTER_COMMIT·fallbackExecution=false 어노테이션과 노출 인자 위임을 확인. 실제 커밋 이벤트 처리는 검증 범위에서 제외. */
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

    /** 기록 서비스가 실패해도 listener 호출자가 예외를 받지 않는지 확인. */
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

    /** executor가 작업 제출을 거부해도 listener가 예외를 밖으로 전파하지 않는지 확인. */
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

    /** 사용자·좌표·요청 ID·두 장소와 추천 버전이 고정된 노출 요청 이벤트를 생성. */
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
