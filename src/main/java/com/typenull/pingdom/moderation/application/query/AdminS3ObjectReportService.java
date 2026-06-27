package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.storage.AdminS3OrphanObjectReportResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminS3ObjectReportService {

    private static final String DEFAULT_PREFIX = "map/";
    private static final int DEFAULT_SCAN_LIMIT = 1_000;
    private static final int MAX_SCAN_LIMIT = 10_000;

    private final MapImageRepository mapImageRepository;
    private final S3ObjectStorage s3ObjectStorage;

    @Transactional(readOnly = true)
    public AdminS3OrphanObjectReportResponse reportOrphanObjects(String prefix, Integer limit) {
        String safePrefix = StringUtils.hasText(prefix) ? prefix.trim() : DEFAULT_PREFIX;
        int safeLimit = normalizeLimit(limit);

        try {
            S3ObjectStorage.S3ListResult s3ListResult = s3ObjectStorage.listKeys(safePrefix, safeLimit);
            List<String> s3Keys = s3ListResult.keys();
            Set<String> usedKeysInBatch = usedS3KeysInBatch(s3Keys);
            List<String> orphanKeys = s3Keys.stream()
                    .filter(key -> !usedKeysInBatch.contains(key))
                    .sorted()
                    .toList();
            long totalDbKeys = mapImageRepository.countOriginalS3Keys()
                    + mapImageRepository.countThumbnailS3Keys();

            return new AdminS3OrphanObjectReportResponse(
                    safePrefix,
                    safeLimit,
                    true,
                    s3ListResult.truncated(),
                    totalDbKeys,
                    s3Keys.size(),
                    orphanKeys.size(),
                    orphanKeys
            );
        } catch (S3StorageException exception) {
            throw toAdminException(exception);
        }
    }

    private Set<String> usedS3KeysInBatch(List<String> s3Keys) {
        Set<String> usedKeys = new HashSet<>();
        if (s3Keys.isEmpty()) {
            return usedKeys;
        }
        usedKeys.addAll(mapImageRepository.findUsedOriginalS3Keys(s3Keys));
        usedKeys.addAll(mapImageRepository.findUsedThumbnailS3Keys(s3Keys));
        return usedKeys;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SCAN_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_SCAN_LIMIT));
    }

    private AdminException toAdminException(S3StorageException exception) {
        if (exception.getError() == S3StorageError.NOT_CONFIGURED) {
            return new AdminException(AdminErrorCode.S3_NOT_CONFIGURED);
        }
        if (exception.getError() == S3StorageError.CONNECTION_ERROR) {
            return new AdminException(AdminErrorCode.S3_CONNECTION_ERROR);
        }
        return new AdminException(AdminErrorCode.S3_REPORT_FAILED, exception);
    }
}
