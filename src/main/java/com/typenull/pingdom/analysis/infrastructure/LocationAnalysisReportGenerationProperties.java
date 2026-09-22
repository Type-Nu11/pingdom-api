package com.typenull.pingdom.analysis.infrastructure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 인스턴스 하나에서 동시에 실행하거나 대기시킬 수 있는 고비용 보고서 생성 작업의 상한.
 * 다중 인스턴스 환경에서는 인스턴스별로 적용되며, 전체 호출량 제한은 Redis rate-limit 정책이 담당.
 */
@Validated
@ConfigurationProperties(prefix = "analysis.location-report-generation")
public record LocationAnalysisReportGenerationProperties(
        @Min(1) Integer maxConcurrent,
        @Min(0) Integer maxQueue
) {

    public LocationAnalysisReportGenerationProperties {
        maxConcurrent = maxConcurrent == null ? 2 : maxConcurrent;
        maxQueue = maxQueue == null ? 4 : maxQueue;
    }
}
