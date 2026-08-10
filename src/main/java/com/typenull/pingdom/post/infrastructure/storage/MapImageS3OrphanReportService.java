package com.typenull.pingdom.post.infrastructure.storage;

import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * MapImage가 사용하는 S3 객체와 S3에만 남은 객체를 같은 기준으로 비교한다.
 *
 * <p>관리자 dry-run 조회와 비동기 전체 리포트가 모두 이 서비스를 사용해야
 * 원본·썸네일 키의 고아 객체 판별 결과가 달라지지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MapImageS3OrphanReportService {

    private static final String MAP_IMAGE_S3_PREFIX = "map/";
    private static final String ORPHAN_REASON = "DB(MapImage)에 존재하지 않는 S3 객체";
    private static final String REPORT_KEY_PREFIX = "pingdom:admin:s3-orphan-report:";
    private static final String LATEST_REPORT_KEY = REPORT_KEY_PREFIX + "latest";
    private static final Duration REPORT_TTL = Duration.ofHours(1);
    private static final int DEFAULT_SCAN_LIMIT = 1_000;
    private static final int MAX_SCAN_LIMIT = 10_000;

    private final S3ObjectStorage s3ObjectStorage;
    private final StringRedisTemplate redisTemplate;
    private final MapImageRepository mapImageRepository;
    private final ExecutorService orphanReportExecutor = Executors.newSingleThreadExecutor();

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

    public S3OrphanReportStatus refreshMapImageS3OrphanReport() {
        String reportId = UUID.randomUUID().toString();
        String metaKey = reportMetaKey(reportId);
        LocalDateTime now = LocalDateTime.now();

        redisTemplate.opsForHash().put(metaKey, "status", "RUNNING");
        redisTemplate.opsForHash().put(metaKey, "generatedAt", now.toString());
        redisTemplate.expire(metaKey, REPORT_TTL);
        redisTemplate.opsForValue().set(LATEST_REPORT_KEY, reportId, REPORT_TTL);

        orphanReportExecutor.execute(() -> buildMapImageS3OrphanReport(reportId));
        return getMapImageS3OrphanReportStatus(reportId);
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

    private void buildMapImageS3OrphanReport(String reportId) {
        String metaKey = reportMetaKey(reportId);
        String candidatesKey = reportCandidatesKey(reportId);
        long s3KeyCount = 0;
        long deleteCandidateCount = 0;

        try {
            redisTemplate.delete(candidatesKey);

            String continuationToken = null;
            do {
                S3ObjectStorage.S3KeyPage s3KeyPage = s3ObjectStorage.listKeysPage(MAP_IMAGE_S3_PREFIX, continuationToken);
                List<String> pageKeys = normalizeKeys(s3KeyPage.keys());
                s3KeyCount += pageKeys.size();

                List<String> candidateKeys = findOrphanKeys(pageKeys);
                if (!candidateKeys.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(candidatesKey, candidateKeys);
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
            redisTemplate.opsForHash().put(metaKey, "status", "FAILED");
            redisTemplate.opsForHash().put(metaKey, "completedAt", LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(metaKey, "errorMessage", exception.getMessage());
            expireReportKeys(reportId);
        }
    }

    private List<String> findOrphanKeys(List<String> s3Keys) {
        if (s3Keys.isEmpty()) {
            return List.of();
        }

        Set<String> usedKeys = new HashSet<>(mapImageRepository.findUsedOriginalS3Keys(s3Keys));
        usedKeys.addAll(mapImageRepository.findUsedThumbnailS3Keys(s3Keys));
        return s3Keys.stream()
                .filter(key -> !usedKeys.contains(key))
                .toList();
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

    @PreDestroy
    void shutdownOrphanReportExecutor() {
        orphanReportExecutor.shutdown();
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
}
