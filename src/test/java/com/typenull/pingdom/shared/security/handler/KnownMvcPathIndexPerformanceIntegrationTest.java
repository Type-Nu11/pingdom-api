package com.typenull.pingdom.shared.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 실제 등록 매핑에서 경로 존재 판정만 비교한다. HTTP 전체 요청 지연을 측정하지 않는다. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class KnownMvcPathIndexPerformanceIntegrationTest {

    private static final int WARMUP_REQUESTS = 500;
    private static final int BATCHES = 20;
    private static final int REQUESTS_PER_BATCH = 100;
    private static volatile int resultSink;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired private KnownMvcPathIndex index;

    @Test
    void comparesRealRegisteredMappingsWithoutRetainingUnknownPaths() throws Exception {
        var thread = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertThat(thread.isCurrentThreadCpuTimeSupported()).isTrue();
        assertThat(thread.isThreadAllocatedMemorySupported()).isTrue();
        if (!thread.isThreadCpuTimeEnabled()) {
            thread.setThreadCpuTimeEnabled(true);
        }
        if (!thread.isThreadAllocatedMemoryEnabled()) {
            thread.setThreadAllocatedMemoryEnabled(true);
        }

        int mappingCount = handlerMapping.getHandlerMethods().size();
        int registeredPatternCount = index.registeredPatternCount();
        int unknownCandidateUpperBound = index.candidatePatternCount(
                PathContainer.parsePath("/unregistered-1836-later/deep"));
        Map<String, Object> cases = new LinkedHashMap<>();
        for (String value : List.of("/users/me", "/places/42", "/admin/places", "/auth/login",
                "/unregistered-1836/deep", "/places/42/unregistered-1836")) {
            PathContainer path = PathContainer.parsePath(value);
            ScanResult scan = scanWithCount(path);
            assertThat(index.matches(path)).as(value).isEqualTo(scan.matches());
            BooleanSupplier baseline = () -> fullScanMatches(path);
            BooleanSupplier current = () -> index.matches(path);
            for (int request = 0; request < WARMUP_REQUESTS; request++) {
                resultSink = (baseline.getAsBoolean() ? 1 : 0) + (current.getAsBoolean() ? 1 : 0);
            }

            List<Sample> baselineSamples = new ArrayList<>();
            List<Sample> indexSamples = new ArrayList<>();
            for (int batch = 0; batch < BATCHES; batch++) {
                // 실행 순서와 직전 작업의 cache 효과가 한쪽에만 고정되지 않도록 교차한다.
                if (batch % 2 == 0) {
                    baselineSamples.add(measure(thread, baseline));
                    indexSamples.add(measure(thread, current));
                } else {
                    indexSamples.add(measure(thread, current));
                    baselineSamples.add(measure(thread, baseline));
                }
            }
            cases.put(value, Map.of(
                    "matches", scan.matches(),
                    "baselinePatternsChecked", scan.checkedPatterns(),
                    "indexCandidateUpperBound", index.candidatePatternCount(path),
                    "baseline", summarize(baselineSamples),
                    "index", summarize(indexSamples)));
        }

        for (int request = 0; request < 10_000; request++) {
            assertThat(index.matches(PathContainer.parsePath("/unregistered-1836-" + request + "/deep")))
                    .isFalse();
        }
        assertThat(index.registeredPatternCount()).isEqualTo(registeredPatternCount);
        assertThat(index.candidatePatternCount(PathContainer.parsePath("/unregistered-1836-later/deep")))
                .isEqualTo(unknownCandidateUpperBound);

        var output = Path.of("build", "reports", "1836-mvc-path-index-cost.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "mappingCount", mappingCount,
                "registeredPatternCount", registeredPatternCount,
                "warmupRequestsPerPath", WARMUP_REQUESTS,
                "batchesPerPath", BATCHES,
                "requestsPerBatch", REQUESTS_PER_BATCH,
                "unknownDistinctRequests", 10_000,
                "javaVersion", System.getProperty("java.version"),
                "cases", cases)));
    }

    /** 60962db의 요청별 getHandlerMethods 및 경로 존재 판정 흐름을 그대로 재현한다. */
    private boolean fullScanMatches(PathContainer path) {
        for (RequestMappingInfo mapping : handlerMapping.getHandlerMethods().keySet()) {
            if (mapping.getPathPatternsCondition() != null) {
                if (mapping.getPathPatternsCondition().getPatterns().stream()
                        .anyMatch(pattern -> pattern.matches(path))) {
                    return true;
                }
            } else if (mapping.getPatternsCondition() != null
                    && !mapping.getPatternsCondition().getMatchingPatterns(path.value()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private ScanResult scanWithCount(PathContainer path) {
        int checked = 0;
        for (RequestMappingInfo mapping : handlerMapping.getHandlerMethods().keySet()) {
            if (mapping.getPathPatternsCondition() != null) {
                for (var pattern : mapping.getPathPatternsCondition().getPatterns()) {
                    checked++;
                    if (pattern.matches(path)) {
                        return new ScanResult(true, checked);
                    }
                }
            } else if (mapping.getPatternsCondition() != null) {
                checked += mapping.getPatternsCondition().getPatterns().size();
                if (!mapping.getPatternsCondition().getMatchingPatterns(path.value()).isEmpty()) {
                    return new ScanResult(true, checked);
                }
            }
        }
        return new ScanResult(false, checked);
    }

    private Sample measure(com.sun.management.ThreadMXBean thread, BooleanSupplier operation) {
        long allocatedBefore = thread.getCurrentThreadAllocatedBytes();
        long cpuBefore = thread.getCurrentThreadCpuTime();
        long started = System.nanoTime();
        int matches = 0;
        for (int request = 0; request < REQUESTS_PER_BATCH; request++) {
            matches += operation.getAsBoolean() ? 1 : 0;
        }
        long elapsed = System.nanoTime() - started;
        long cpu = thread.getCurrentThreadCpuTime() - cpuBefore;
        long allocated = thread.getCurrentThreadAllocatedBytes() - allocatedBefore;
        resultSink = matches;
        return new Sample(elapsed, cpu, allocated);
    }

    private Map<String, Object> summarize(List<Sample> samples) {
        double[] nanos = samples.stream().mapToDouble(sample -> (double) sample.wallNanos() / REQUESTS_PER_BATCH)
                .sorted().toArray();
        return Map.of(
                "batchMeanMedianMicros", nanos[nanos.length / 2] / 1_000.0,
                "batchMeanP95Micros", nanos[(int) Math.ceil(nanos.length * 0.95) - 1] / 1_000.0,
                "cpuNanosPerRequest", samples.stream().mapToLong(Sample::cpuNanos).sum()
                        / ((long) samples.size() * REQUESTS_PER_BATCH),
                "allocatedBytesPerRequest", samples.stream().mapToLong(Sample::allocatedBytes).sum()
                        / ((long) samples.size() * REQUESTS_PER_BATCH));
    }

    private record ScanResult(boolean matches, int checkedPatterns) {}
    private record Sample(long wallNanos, long cpuNanos, long allocatedBytes) {}
}
