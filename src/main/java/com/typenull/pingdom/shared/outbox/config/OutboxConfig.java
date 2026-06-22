package com.typenull.pingdom.shared.outbox.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

    @Bean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }
}
