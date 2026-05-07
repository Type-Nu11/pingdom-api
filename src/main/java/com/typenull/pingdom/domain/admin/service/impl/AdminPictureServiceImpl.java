package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<S3Client> s3ClientProvider;

    @Value("${spring.cloud.aws.s3.bucket:}")
    private String bucket;

    @Override
    @Transactional
    public void deletePicture(Long pictureId) {
        MapImage mapImage = mapImageRepository.findById(pictureId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PICTURE_NOT_FOUND));

        String keyToDelete = resolveS3Key(mapImage);
        if (StringUtils.hasText(keyToDelete)) {
            deleteFromS3(keyToDelete);
        }

        mapImageRepository.delete(mapImage);
    }

    private void deleteFromS3(String s3Key) {
        if (!StringUtils.hasText(bucket)) {
            // S3 키가 있는데 설정이 없으면 DB만 지우면 안 됨 (데이터 불일치 방지)
            throw new AdminException(AdminErrorCode.S3_NOT_CONFIGURED);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new AdminException(AdminErrorCode.S3_NOT_CONFIGURED);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
        } catch (S3Exception exception) {
            log.error("S3 삭제 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new AdminException(AdminErrorCode.PICTURE_DELETE_FAILED, exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new AdminException(AdminErrorCode.S3_CONNECTION_ERROR, exception);
        }
    }

    private String resolveS3Key(MapImage mapImage) {
        if (StringUtils.hasText(mapImage.getS3Key())) {
            return mapImage.getS3Key();
        }
        return extractKeyFromUrlIfPossible(mapImage.getImageUrl());
    }

    // imageUrl이 S3 URL(virtual-hosted style / path style)일 때 key를 추출한다.
    private String extractKeyFromUrlIfPossible(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        try {
            URI uri = new URI(imageUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null) {
                return null;
            }

            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            if (!StringUtils.hasText(normalizedPath)) {
                return null;
            }

            // virtual-hosted style: <bucket>.s3.<region>.amazonaws.com/<key>
            if (host.startsWith(bucket + ".") && host.contains(".amazonaws.com")) {
                return normalizedPath;
            }

            // path style: s3.<region>.amazonaws.com/<bucket>/<key>
            if (host.startsWith("s3.") && host.contains(".amazonaws.com") && normalizedPath.startsWith(bucket + "/")) {
                return normalizedPath.substring((bucket + "/").length());
            }
        } catch (URISyntaxException ignored) {
            return null;
        }

        return null;
    }
}
