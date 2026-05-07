package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final MapImageRepository mapImageRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    @Transactional
    public void uploadImage(ImageUploadRequest request, long userId) throws IOException {
        if (!StringUtils.hasText(bucket)) {
            throw new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }

        // 파일명 중복 방지 (UUID + 원본파일명)
        String originalFilename = request.file().getOriginalFilename();
        String s3FileName = UUID.randomUUID() + "-" + (originalFilename != null ? originalFilename : "unnamed");

        // S3에 저장할 데이터
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3FileName)
                .contentLength(request.file().getSize())
                .contentType(request.file().getContentType())
                .build();

        // 업로드할 오브젝트
        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                request.file().getInputStream(),
                request.file().getSize()
        ));

        // 파일의 URL 저장
        try {
            // DB 저장 시도
            String imageUrl = s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucket).key(s3FileName).build())
                    .toExternalForm();

            MapImage mapImage = MapImage.builder()
                    .imageUrl(imageUrl)
                    .s3Key(s3FileName)
                    .userId(userId)
                    .build();

            mapImageRepository.save(mapImage);

        } catch (Exception e) {
            // DB 저장 실패 시 S3 파일 삭제
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3FileName)
                    .build());
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        }
    }

    @Transactional
    public void deleteImage(Long imageId, Long userId) {
        // 지우려는 이미지가 있는지
        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        // 본인이 맞는지
        if (!mapImage.getUserId().equals(userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_DELETED);
        }

        deleteFromS3(mapImage.getS3Key());
        mapImageRepository.delete(mapImage);
    }

    // 삭제 메서드
    private void deleteFromS3(String s3Key) {
        if (!StringUtils.hasText(bucket)) {
            throw new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());

        } catch (S3Exception e) {
            // AWS 서버 에러 (권한 부족, 네트워크 문제 등)
            log.error("S3 삭제 실패: {}", e.awsErrorDetails().errorMessage());
            throw new MapException(MapErrorCode.DELETE_ERROR);

        } catch (SdkException e) {
            // 클라이언트 측 네트워크 문제
            log.error("S3 연결 실패: {}", e.getMessage());
            throw new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
    }
}
