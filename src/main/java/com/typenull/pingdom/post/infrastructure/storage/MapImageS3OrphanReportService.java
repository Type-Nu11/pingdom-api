package com.typenull.pingdom.post.infrastructure.storage;

import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * MapImage가 사용하는 S3 객체와 S3에만 남은 객체를 같은 기준으로 비교.
 *
 * <p>관리자 dry-run 조회와 비동기 전체 리포트가 모두 이 서비스를 사용해
 * 원본·썸네일 키의 고아 객체 판별 결과를 통일.</p>
 */
@Service
@Slf4j
public class MapImageS3OrphanReportService {

    private static final String MAP_IMAGE_S3_PREFIX = "map/";
    private static final String ORPHAN_REASON = "DB(MapImage)에 존재하지 않는 S3 객체";
    private static final String REPORT_KEY_PREFIX = "pingdom:admin:s3-orphan-report:";
    private static final String LATEST_REPORT_KEY = REPORT_KEY_PREFIX + "latest";
    private static final Duration REPORT_TTL = Duration.ofHours(1);
    private static final int DEFAULT_SCAN_LIMIT = 1_000;
    private static final int MAX_SCAN_LIMIT = 10_000;
    private static final String EXECUTOR_SATURATED_MESSAGE = "S3 고아 파일 리포트 생성 대기열이 포화되었습니다.";

    private final S3ObjectStorage s3ObjectStorage;
    private final StringRedisTemplate redisTemplate;
    private final MapImageRepository mapImageRepository;
    private final TaskExecutor orphanReportExecutor;
    private final Object refreshMonitor = new Object();

    private String runningReportId;

    public MapImageS3OrphanReportService(
            S3ObjectStorage s3ObjectStorage,
            StringRedisTemplate redisTemplate,
            MapImageRepository mapImageRepository,
            @Qualifier("s3OrphanReportExecutor") TaskExecutor orphanReportExecutor
    ) {
        this.s3ObjectStorage = s3ObjectStorage;
        this.redisTemplate = redisTemplate;
        this.mapImageRepository = mapImageRepository;
        this.orphanReportExecutor = orphanReportExecutor;
    }

    @Transactional(readOnly = true)
    public S3OrphanDryRunReport reportOrphanObjects(String prefix, Integer limit) {
        String safePrefix = StringUtils.hasText(prefix) ? prefix.trim() : MAP_IMAGE_S3_PREFIX;
        int safeLimit = normalizeScanLimit(limit);
        S3ObjectStorage.S3ListResult s3ListResult = s3ObjectStorage.listKeys(safePrefix, safeLimit);
        List<String> s3Keys = normalizeKeys(s3ListResult.keys());
        List<String> orphanKeys = findOrphanKeys(s3Keys).stream()
                .sorted()
                .toList();

        return new S3OrphanDryRunReport(
                safePrefix,
                safeLimit,
                s3ListResult.truncated(),
                countRegisteredMapImageKeys(),
                s3Keys.size(),
                orphanKeys.size(),
                orphanKeys
        );
    }

    /**
     * 한 JVM에서 진행 중인 작업을 재사용하고 전용 실행기에 S3 전체 순회 작업을 제출.
     * Redis에 보관하는 결과의 수명은 1시간이며, JVM 간 실행 중복을 막는 분산 잠금은 없음.
     * 대기열 포화는 FAILED 리포트로 남기고 부분 스캔 실패의 자동 재시도는 미지원.
     */
    public S3OrphanReportStatus refreshMapImageS3OrphanReport() {
        synchronized (refreshMonitor) {
            if (runningReportId != null) {
                return getMapImageS3OrphanReportStatus(runningReportId);
            }

            String reportId = UUID.randomUUID().toString();
            initializeRunningReport(reportId);
            runningReportId = reportId;

            try {
                orphanReportExecutor.execute(() -> {
                    try {
                        buildMapImageS3OrphanReport(reportId);
                    } finally {
                        clearRunningReport(reportId);
                    }
                });
            } catch (TaskRejectedException exception) {
                log.warn("S3 고아 파일 리포트 생성 작업 제출이 거부되었습니다. reportId={}", reportId, exception);
                failReport(reportId, EXECUTOR_SATURATED_MESSAGE);
                clearRunningReport(reportId);
            }

            return getMapImageS3OrphanReportStatus(reportId);
        }
    }

    public S3OrphanReport getMapImageS3OrphanReport(String reportId, int page, int limit) {
        String resolvedReportId = resolveReportId(reportId);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String metaKey = reportMetaKey(resolvedReportId);
        String candidatesKey = reportCandidatesKey(resolvedReportId);
        String status = readMeta(metaKey, "status", "NOT_FOUND");

        long deleteCandidateCount = parseLong(readMeta(metaKey, "deleteCandidateCount", "0"));
        long totalPages = (long) Math.ceil((double) deleteCandidateCount / safeLimit);
        long fromIndex = Math.min((long) (safePage - 1) * safeLimit, deleteCandidateCount);
        long toIndex = Math.min(fromIndex + safeLimit, deleteCandidateCount) - 1;
        List<String> pagedKeys = toIndex < fromIndex
                ? List.of()
                : redisTemplate.opsForList().range(candidatesKey, fromIndex, toIndex);
        List<S3OrphanCandidate> deleteCandidates = (pagedKeys == null ? List.<String>of() : pagedKeys)
                .stream()
                .map(key -> new S3OrphanCandidate(key, ORPHAN_REASON))
                .toList();

        return new S3OrphanReport(
                resolvedReportId,
                status,
                readMeta(metaKey, "errorMessage", null),
                deleteCandidates,
                parseLong(readMeta(metaKey, "dbKeyCount", "0")),
                parseLong(readMeta(metaKey, "s3KeyCount", "0")),
                deleteCandidateCount,
                parseDateTime(readMeta(metaKey, "generatedAt", null)),
                safePage,
                safeLimit,
                deleteCandidateCount,
                totalPages,
                safePage < totalPages
        );
    }

    public S3OrphanReportStatus getMapImageS3OrphanReportStatus(String reportId) {
        String resolvedReportId = resolveReportId(reportId);
        String metaKey = reportMetaKey(resolvedReportId);
        return new S3OrphanReportStatus(
                resolvedReportId,
                readMeta(metaKey, "status", "NOT_FOUND"),
                parseDateTime(readMeta(metaKey, "generatedAt", null)),
                parseDateTime(readMeta(metaKey, "completedAt", null)),
                parseLong(readMeta(metaKey, "dbKeyCount", "0")),
                parseLong(readMeta(metaKey, "s3KeyCount", "0")),
                parseLong(readMeta(metaKey, "deleteCandidateCount", "0")),
                readMeta(metaKey, "errorMessage", null)
        );
    }

    /**
     * 완료 리포트의 후보인지와 현재 DB 사용 여부를 재확인한 뒤 S3 객체를 개별 삭제.
     * map/ 접두어 밖의 입력은 제외하며, 일부 삭제 실패 시 성공한 객체를 복구하지 않고 항목별 결과를 반환.
     * DB 재확인과 S3 삭제 사이에 전역 잠금이 없으므로 동시 참조 생성의 차단은 보장 범위 외.
     */
    public S3OrphanDeleteResult deleteMapImageS3Candidates(String reportId, List<String> keys) {
        Set<String> requestedKeys = normalizeDeleteKeys(keys);
        if (requestedKeys.isEmpty()) {
            return new S3OrphanDeleteResult(0, 0, 0, List.of(), List.of());
        }

        String resolvedReportId = resolveReportId(reportId);
        String reportStatus = readMeta(reportMetaKey(resolvedReportId), "status", "NOT_FOUND");
        if (!"COMPLETED".equals(reportStatus)) {
            return failedDeleteResult(requestedKeys, "완료된 S3 고아 파일 리포트가 아닙니다.");
        }

        String candidateSetKey = reportCandidateSetKey(resolvedReportId);
        List<String> reportCandidateKeys = new ArrayList<>();
        List<S3OrphanDeleteFailure> failedKeys = new ArrayList<>();
        for (String key : requestedKeys) {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(candidateSetKey, key))) {
                reportCandidateKeys.add(key);
            } else {
                failedKeys.add(new S3OrphanDeleteFailure(key, "리포트 삭제 후보에 없는 S3 객체입니다."));
            }
        }

        Set<String> usedKeys = findUsedKeys(reportCandidateKeys);
        List<String> deletableKeys = new ArrayList<>();
        for (String key : reportCandidateKeys) {
            if (usedKeys.contains(key)) {
                failedKeys.add(new S3OrphanDeleteFailure(key, "DB(MapImage)에서 사용 중인 S3 객체입니다."));
            } else {
                deletableKeys.add(key);
            }
        }

        List<String> deletedKeys = new ArrayList<>();
        for (String key : deletableKeys) {
            try {
                s3ObjectStorage.delete(key);
                deletedKeys.add(key);
                log.info("S3 고아 파일 삭제 성공. reportId={}, key={}", resolvedReportId, key);
            } catch (RuntimeException exception) {
                failedKeys.add(new S3OrphanDeleteFailure(key, exception.getMessage()));
                log.warn("S3 고아 파일 삭제 실패. reportId={}, key={}", resolvedReportId, key, exception);
            }
        }

        return new S3OrphanDeleteResult(
                requestedKeys.size(),
                deletedKeys.size(),
                failedKeys.size(),
                deletedKeys,
                failedKeys
        );
    }

    private void buildMapImageS3OrphanReport(String reportId) {
        String metaKey = reportMetaKey(reportId);
        String candidatesKey = reportCandidatesKey(reportId);
        String candidateSetKey = reportCandidateSetKey(reportId);
        long s3KeyCount = 0;
        long deleteCandidateCount = 0;

        try {
            redisTemplate.delete(List.of(candidatesKey, candidateSetKey));

            String continuationToken = null;
            do {
                S3ObjectStorage.S3KeyPage s3KeyPage = s3ObjectStorage.listKeysPage(MAP_IMAGE_S3_PREFIX, continuationToken);
                List<String> pageKeys = normalizeKeys(s3KeyPage.keys());
                s3KeyCount += pageKeys.size();

                List<String> candidateKeys = findOrphanKeys(pageKeys);
                if (!candidateKeys.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(candidatesKey, candidateKeys);
                    redisTemplate.opsForSet().add(candidateSetKey, candidateKeys.toArray(String[]::new));
                    deleteCandidateCount += candidateKeys.size();
                }
                continuationToken = s3KeyPage.nextContinuationToken();
            } while (continuationToken != null);

            redisTemplate.opsForHash().put(metaKey, "status", "COMPLETED");
            redisTemplate.opsForHash().put(metaKey, "completedAt", LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(metaKey, "dbKeyCount", String.valueOf(countRegisteredMapImageKeys()));
            redisTemplate.opsForHash().put(metaKey, "s3KeyCount", String.valueOf(s3KeyCount));
            redisTemplate.opsForHash().put(metaKey, "deleteCandidateCount", String.valueOf(deleteCandidateCount));
            expireReportKeys(reportId);
        } catch (RuntimeException exception) {
            log.warn("S3 고아 파일 리포트 생성 실패. reportId={}", reportId, exception);
            failReport(reportId, exception.getMessage());
        }
    }

    private void initializeRunningReport(String reportId) {
        String metaKey = reportMetaKey(reportId);
        redisTemplate.opsForHash().put(metaKey, "status", "RUNNING");
        redisTemplate.opsForHash().put(metaKey, "generatedAt", LocalDateTime.now().toString());
        redisTemplate.expire(metaKey, REPORT_TTL);
        redisTemplate.opsForValue().set(LATEST_REPORT_KEY, reportId, REPORT_TTL);
    }

    private void failReport(String reportId, String errorMessage) {
        String metaKey = reportMetaKey(reportId);
        redisTemplate.opsForHash().put(metaKey, "status", "FAILED");
        redisTemplate.opsForHash().put(metaKey, "completedAt", LocalDateTime.now().toString());
        redisTemplate.opsForHash().put(metaKey, "errorMessage", errorMessage);
        expireReportKeys(reportId);
    }

    private void clearRunningReport(String reportId) {
        synchronized (refreshMonitor) {
            if (reportId.equals(runningReportId)) {
                runningReportId = null;
            }
        }
    }

    private List<String> findOrphanKeys(List<String> s3Keys) {
        if (s3Keys.isEmpty()) {
            return List.of();
        }

        Set<String> usedKeys = findUsedKeys(s3Keys);
        return s3Keys.stream()
                .filter(key -> !usedKeys.contains(key))
                .toList();
    }

    private Set<String> findUsedKeys(List<String> keys) {
        if (keys.isEmpty()) {
            return Set.of();
        }

        Set<String> usedKeys = new HashSet<>(mapImageRepository.findUsedOriginalS3Keys(keys));
        usedKeys.addAll(mapImageRepository.findUsedThumbnailS3Keys(keys));
        return usedKeys;
    }

    private Set<String> normalizeDeleteKeys(List<String> keys) {
        if (keys == null) {
            return Set.of();
        }

        return keys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(key -> key.startsWith(MAP_IMAGE_S3_PREFIX))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private S3OrphanDeleteResult failedDeleteResult(Set<String> requestedKeys, String reason) {
        List<S3OrphanDeleteFailure> failedKeys = requestedKeys.stream()
                .map(key -> new S3OrphanDeleteFailure(key, reason))
                .toList();
        return new S3OrphanDeleteResult(requestedKeys.size(), 0, failedKeys.size(), List.of(), failedKeys);
    }

    private List<String> normalizeKeys(List<String> keys) {
        return keys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private long countRegisteredMapImageKeys() {
        return mapImageRepository.countOriginalS3Keys() + mapImageRepository.countThumbnailS3Keys();
    }

    private int normalizeScanLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SCAN_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_SCAN_LIMIT));
    }

    private String resolveReportId(String reportId) {
        if (StringUtils.hasText(reportId)) {
            return reportId.trim();
        }
        String latestReportId = redisTemplate.opsForValue().get(LATEST_REPORT_KEY);
        if (!StringUtils.hasText(latestReportId)) {
            throw new IllegalStateException("생성된 S3 고아 파일 리포트가 없습니다.");
        }
        return latestReportId;
    }

    private void expireReportKeys(String reportId) {
        redisTemplate.expire(reportMetaKey(reportId), REPORT_TTL);
        redisTemplate.expire(reportCandidatesKey(reportId), REPORT_TTL);
        redisTemplate.expire(reportCandidateSetKey(reportId), REPORT_TTL);
    }

    private String readMeta(String metaKey, String field, String defaultValue) {
        Object value = redisTemplate.opsForHash().get(metaKey, field);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String reportMetaKey(String reportId) {
        return REPORT_KEY_PREFIX + reportId + ":meta";
    }

    private String reportCandidatesKey(String reportId) {
        return REPORT_KEY_PREFIX + reportId + ":candidates";
    }

    private String reportCandidateSetKey(String reportId) {
        return REPORT_KEY_PREFIX + reportId + ":candidate-set";
    }

    public record S3OrphanDryRunReport(
            String prefix,
            int scanLimit,
            boolean truncated,
            long dbKeyCount,
            long s3ObjectCount,
            long orphanObjectCount,
            List<String> orphanKeys
    ) {
    }

    public record S3OrphanCandidate(String key, String reason) {
    }

    public record S3OrphanReportStatus(
            String reportId,
            String status,
            LocalDateTime generatedAt,
            LocalDateTime completedAt,
            long dbKeyCount,
            long s3KeyCount,
            long deleteCandidateCount,
            String errorMessage
    ) {
    }

    public record S3OrphanReport(
            String reportId,
            String status,
            String errorMessage,
            List<S3OrphanCandidate> deleteCandidates,
            long dbKeyCount,
            long s3KeyCount,
            long deleteCandidateCount,
            LocalDateTime generatedAt,
            int page,
            int limit,
            long totalCount,
            long totalPages,
            boolean hasNext
    ) {
    }

    public record S3OrphanDeleteFailure(String key, String reason) {
    }

    public record S3OrphanDeleteResult(
            int requestedKeyCount,
            int deletedKeyCount,
            int failedKeyCount,
            List<String> deletedKeys,
            List<S3OrphanDeleteFailure> failedKeys
    ) {
    }
}
