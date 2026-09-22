package com.typenull.pingdom.post.infrastructure.storage;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * S3 전체 스캔을 작업자 1개·대기 작업 1개로 제한. 포화 시 호출자에게 거절 예외를 전달하며,
 * 종료 시 작업 완료를 기다리는 시간은 최대 30초.
 */
@Configuration
public class S3OrphanReportExecutorConfig {

    private static final int WORKER_COUNT = 1;
    private static final int QUEUE_CAPACITY = 1;

    @Bean(name = "s3OrphanReportExecutor")
    public ThreadPoolTaskExecutor s3OrphanReportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(WORKER_COUNT);
        executor.setMaxPoolSize(WORKER_COUNT);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("s3-orphan-report-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
