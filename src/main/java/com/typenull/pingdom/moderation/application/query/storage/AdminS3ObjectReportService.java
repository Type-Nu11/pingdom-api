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

/**
 * 공유 고아 객체 판별 서비스의 제한된 스캔 결과를 관리자 dry-run 응답으로 변환.
 * 객체 삭제 없이 조회만 수행하며 truncated는 전체 S3 공간 중 일부만 조사한 결과. 저장소 설정·연결 오류는 관리자 오류로 매핑.
 */
@Service
@RequiredArgsConstructor
public class AdminS3ObjectReportService {

    private final MapImageS3OrphanReportService mapImageS3OrphanReportService;

    /**
     * 제한된 S3 스캔과 DB 참조 비교 결과를 객체 삭제 없는 dry-run 응답으로 반환.
     * 저장소 미설정·연결 실패·그 외 S3 오류를 구분해 관리자 오류로 변환하며 truncated 결과는 버킷 일부 조사에 한정.
     */
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
