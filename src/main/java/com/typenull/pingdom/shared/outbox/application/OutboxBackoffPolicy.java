package com.typenull.pingdom.shared.outbox.application;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 실패 횟수에 따라 지수형 재시도 간격을 계산하고 설정된 최대 간격으로 제한한다. */
@Component
@RequiredArgsConstructor
public class OutboxBackoffPolicy {

    private final OutboxProperties properties;

    /** 첫 실패는 baseBackoff, 이후는 2배씩 증가한다. 지수는 30에서 제한하며 Duration 곱셈 overflow도 maxBackoff로 처리한다. */
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
