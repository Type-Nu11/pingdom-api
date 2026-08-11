package com.typenull.pingdom.post.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class S3OrphanReportExecutorConfigTest {

    @Test
    void rejectsAdditionalTaskWhenWorkerAndQueueAreFull() throws InterruptedException {
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
