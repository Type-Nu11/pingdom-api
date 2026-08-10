package com.typenull.pingdom.moderation.application.query.storage;

import com.typenull.pingdom.moderation.api.dto.storage.AdminS3OrphanObjectReportResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.post.infrastructure.storage.MapImageS3OrphanReportService;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminS3ObjectReportService {

    private final MapImageS3OrphanReportService mapImageS3OrphanReportService;

    public AdminS3OrphanObjectReportResponse reportOrphanObjects(String prefix, Integer limit) {
        try {
            MapImageS3OrphanReportService.S3OrphanDryRunReport report =
                    mapImageS3OrphanReportService.reportOrphanObjects(prefix, limit);

            return new AdminS3OrphanObjectReportResponse(
                    report.prefix(),
                    report.scanLimit(),
                    true,
                    report.truncated(),
                    report.dbKeyCount(),
                    report.s3ObjectCount(),
                    report.orphanObjectCount(),
                    report.orphanKeys()
            );
        } catch (S3StorageException exception) {
            throw toAdminException(exception);
        }
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
