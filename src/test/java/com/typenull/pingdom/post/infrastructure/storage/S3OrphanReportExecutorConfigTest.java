package com.typenull.pingdom.post.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class S3OrphanReportExecutorConfigTest {

    /**
     * 실제 executor의 실행 작업을 latch로 보류하고 대기열 한 칸을 채우면 다음 작업이 TaskRejectedException인지 검증.
     * finally에서 작업을 해제하고 executor를 종료해 작업 스레드가 남지 않게 함.
     */
    @Test
    void rejectsSaturatedOrphanExecutor() throws InterruptedException {
        ThreadPoolTaskExecutor executor = new S3OrphanReportExecutorConfig().s3OrphanReportExecutor();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                taskStarted.countDown();
                try {
                    releaseTask.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

            executor.execute(() -> {
            });

            assertThrows(TaskRejectedException.class, () -> executor.execute(() -> {
            }));
        } finally {
            releaseTask.countDown();
            executor.shutdown();
        }
    }
}
