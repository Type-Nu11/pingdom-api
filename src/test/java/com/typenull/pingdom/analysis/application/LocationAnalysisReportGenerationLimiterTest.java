package com.typenull.pingdom.analysis.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportGenerationProperties;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LocationAnalysisReportGenerationLimiterTest {

    /** 실행 1개와 대기 1개가 찬 상태의 세 번째 요청은 외부 작업을 시작하지 않고 즉시 거절. */
    @Test
    void rejectsRequestWhenExecutionAndQueueAreFull() throws Exception {
        LocationAnalysisReportGenerationLimiter limiter = limiter(1, 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean thirdStarted = new AtomicBoolean(false);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> limiter.execute(() -> {
                firstStarted.countDown();
                await(releaseFirst);
                return "first";
            }));
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            var second = executor.submit(() -> limiter.execute(() -> {
                secondStarted.countDown();
                return "second";
            }));
            assertThrows(RateLimitException.class, () -> limiter.execute(() -> {
                thirdStarted.set(true);
                return "third";
            }));
            assertFalse(thirdStarted.get());

            releaseFirst.countDown();
            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
            assertEquals("second", second.get(1, TimeUnit.SECONDS));
        }
    }

    /** AI·PDF 작업이 예외로 끝나도 실행 슬롯과 대기 자리가 다음 요청에 반환되는지 검증. */
    @Test
    void releasesSlotsAfterOperationFailure() {
        LocationAnalysisReportGenerationLimiter limiter = limiter(1, 0);

        assertThrows(IllegalStateException.class, () -> limiter.execute(() -> {
            throw new IllegalStateException("AI 실패");
        }));
        assertEquals("recovered", limiter.execute(() -> "recovered"));
    }

    private LocationAnalysisReportGenerationLimiter limiter(int maxConcurrent, int maxQueue) {
        return new LocationAnalysisReportGenerationLimiter(
                new LocationAnalysisReportGenerationProperties(maxConcurrent, maxQueue)
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
