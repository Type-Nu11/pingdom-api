package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.storage.AdminS3OrphanObjectReportResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.util.LinkedHashSet;
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
        Set<String> usedKeys = usedS3Keys();

        try {
            S3ObjectStorage.S3ListResult s3ListResult = s3ObjectStorage.listKeys(safePrefix, safeLimit);
            List<String> orphanKeys = s3ListResult.keys().stream()
                    .filter(key -> !usedKeys.contains(key))
                    .sorted()
                    .toList();

            return new AdminS3OrphanObjectReportResponse(
                    safePrefix,
                    safeLimit,
                    true,
                    s3ListResult.truncated(),
                    usedKeys.size(),
                    s3ListResult.keys().size(),
                    orphanKeys.size(),
                    orphanKeys
            );
        } catch (S3StorageException exception) {
            throw toAdminException(exception);
        }
    }

    private Set<String> usedS3Keys() {
        Set<String> usedKeys = new LinkedHashSet<>();
        usedKeys.addAll(mapImageRepository.findAllOriginalS3Keys());
        usedKeys.addAll(mapImageRepository.findAllThumbnailS3Keys());
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
