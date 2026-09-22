package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportGenerationProperties;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * AI 호출부터 PDF·DB 보관까지의 보고서 생성 작업 수를 인스턴스 단위로 제한.
 * admission은 실행 중인 작업과 대기 작업을 함께 세므로, 가득 찬 요청은 외부 작업 시작 전에 즉시 거절.
 */
@Component
public class LocationAnalysisReportGenerationLimiter {

    private static final String EXCEEDED_MESSAGE = "보고서 생성 요청이 많습니다. 잠시 후 다시 시도해주세요.";

    private final Semaphore admission;
    private final Semaphore execution;

    public LocationAnalysisReportGenerationLimiter(LocationAnalysisReportGenerationProperties properties) {
        this.admission = new Semaphore(properties.maxConcurrent() + properties.maxQueue(), true);
        this.execution = new Semaphore(properties.maxConcurrent(), true);
    }

    /** 대기열 자리를 먼저 확보한 요청만 실행 슬롯을 기다리고, 성공·실패·인터럽트 모두에서 확보한 자원을 반환. */
    public <T> T execute(Supplier<T> operation) {
        if (!admission.tryAcquire()) {
            throw new RateLimitException(EXCEEDED_MESSAGE);
        }

        boolean executionAcquired = false;
        try {
            execution.acquire();
            executionAcquired = true;
            return operation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RateLimitException(EXCEEDED_MESSAGE);
        } finally {
            if (executionAcquired) {
                execution.release();
            }
            admission.release();
        }
    }
}
