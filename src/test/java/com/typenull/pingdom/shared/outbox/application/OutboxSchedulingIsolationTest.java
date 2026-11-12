package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class OutboxSchedulingIsolationTest {
    @Test
    void heartbeatDoesNotReplaceApplicationTaskScheduler() {
        OutboxProperties properties = new OutboxProperties(20, 2, 40, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingFixture.class)
                .withBean(OutboxLeaseManager.class, () -> new OutboxLeaseManager(
                        mock(OutboxEventStateService.class), properties, mock(OutboxMetrics.class)))
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskScheduler.class);
                    assertThat(context.getBean(TaskScheduler.class)).isInstanceOf(ThreadPoolTaskScheduler.class);
                    assertThat(context.getBeansOfType(ScheduledExecutorService.class)).isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingFixture {
    }
}
