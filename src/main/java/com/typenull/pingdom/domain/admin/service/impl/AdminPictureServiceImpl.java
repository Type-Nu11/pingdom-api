package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPictureServiceImpl implements AdminPictureService {

    private final MapImageRepository mapImageRepository;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket:}")
    private String bucket;

    @Override
    @Transactional
    public void deletePicture(Long pictureId) {
        MapImage mapImage = mapImageRepository.findById(pictureId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PICTURE_NOT_FOUND));

        if (StringUtils.hasText(mapImage.getS3Key())) {
            deleteFromS3(mapImage.getS3Key());
        }

        mapImageRepository.delete(mapImage);
    }

    private void deleteFromS3(String s3Key) {
        if (!StringUtils.hasText(bucket)) {
            throw new AdminException(AdminErrorCode.PICTURE_DELETE_FAILED);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
        } catch (S3Exception exception) {
            log.error("S3 삭제 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new AdminException(AdminErrorCode.PICTURE_DELETE_FAILED);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new AdminException(AdminErrorCode.S3_CONNECTION_ERROR);
        }
    }
}
