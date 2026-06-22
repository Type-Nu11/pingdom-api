package com.typenull.pingdom.shared.outbox.application;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxBackoffPolicy {

    private final OutboxProperties properties;

    public Duration calculateDelay(int attemptNumber) {
        long multiplier = 1L << Math.min(Math.max(attemptNumber - 1, 0), 30);
        Duration delay;
        try {
            delay = properties.baseBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.maxBackoff();
        }
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }
}
