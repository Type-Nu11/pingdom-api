package com.typenull.pingdom.shared.outbox.config;

import com.typenull.pingdom.shared.outbox.application.OutboxProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class OutboxConfig {

    @Bean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }

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
