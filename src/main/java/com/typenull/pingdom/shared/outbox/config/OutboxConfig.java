package com.typenull.pingdom.shared.outbox.config;

import com.typenull.pingdom.shared.outbox.application.OutboxProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Outbox 전용 UTC 시계와 제한된 병렬 worker executor를 제공. */
@Configuration
public class OutboxConfig {

    /** 서버 로컬 시간대와 무관하게 Outbox 예약·보관 판정에 UTC를 사용. */
    @Bean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }

    /** 고정 동시성·설정 큐 크기의 executor를 만들고 종료 시 진행 작업을 최대 30초 기다리도록 설정. */
    @Bean
    public ThreadPoolTaskExecutor outboxExecutor(OutboxProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerConcurrency());
        executor.setMaxPoolSize(properties.workerConcurrency());
        executor.setQueueCapacity(properties.workerQueueCapacity());
        executor.setThreadNamePrefix("outbox-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
