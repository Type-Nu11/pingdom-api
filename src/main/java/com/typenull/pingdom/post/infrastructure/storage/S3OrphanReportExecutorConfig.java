package com.typenull.pingdom.post.infrastructure.storage;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
