package com.typenull.pingdom.domain.map.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.global.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final AmazonS3 amazonS3;
    private final AwsProperties awsProperties;
    private final MapImageRepository mapImageRepository;

    @Transactional
    public void uploadImage(ImageUploadRequest request, long userId) throws IOException {
        // 파일명 중복 방지 (UUID + 원본파일명)
        String originalFilename = request.file().getOriginalFilename();
        String s3FileName = UUID.randomUUID() + "-" + (originalFilename != null ? originalFilename : "unnamed");

        // S3에 저장할 데이터
        ObjectMetadata objMeta = new ObjectMetadata();
        objMeta.setContentLength(request.file().getSize()); // 파일 사이즈, 최대치는 yml에서 조절
        objMeta.setContentType(request.file().getContentType()); // 파일 타입

        // 업로드할 오브젝트
        amazonS3.putObject(
                awsProperties.getS3().getBucket(),
                s3FileName,
                request.file().getInputStream(),
                objMeta
        );

        // 파일의 URL 저장
        try {
            // DB 저장 시도
            MapImage mapImage = MapImage.builder()
                    .imageUrl(amazonS3.getUrl(awsProperties.getS3().getBucket(), s3FileName).toString())
                    .s3Key(s3FileName)
                    .userId(userId)
                    .build();

            mapImageRepository.save(mapImage);

        } catch (Exception e) {
            // DB 저장 실패 시 S3 파일 삭제
            amazonS3.deleteObject(awsProperties.getS3().getBucket(), s3FileName);
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
        try {
            amazonS3.deleteObject(awsProperties.getS3().getBucket(), s3Key);

        } catch (com.amazonaws.AmazonServiceException e) {
            // AWS 서버 에러 (권한 부족, 네트워크 문제 등)
            log.error("S3 삭제 실패: {}", e.getErrorMessage());
            throw new MapException(MapErrorCode.DELETE_ERROR);

        } catch (com.amazonaws.SdkClientException e) {
            // 클라이언트 측 네트워크 문제
            log.error("S3 연결 실패: {}", e.getMessage());
            throw new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
    }
}