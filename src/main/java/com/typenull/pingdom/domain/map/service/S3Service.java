package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.dto.MapImageUploadResponse;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.PictureReportRepository;
import com.typenull.pingdom.global.s3.S3ObjectStorage;
import com.typenull.pingdom.global.s3.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.global.s3.S3ObjectStorage.S3StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3ObjectStorage s3ObjectStorage;
    private final MapImageRepository mapImageRepository;
    private final PictureReportRepository pictureReportRepository;

    @Transactional
    public MapImageUploadResponse uploadImage(ImageUploadRequest request, long userId) throws IOException {
        S3ObjectStorage.S3PutResult putResult;
        try {
            putResult = s3ObjectStorage.put(request.file(), "map");
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }

        // 파일의 URL 저장
        try {
            MapImage mapImage = MapImage.builder()
                    .imageUrl(putResult.url())
                    .s3Key(putResult.key())
                    .userId(userId)
                    .build();

            MapImage saved = mapImageRepository.save(mapImage);
            return new MapImageUploadResponse(
                    saved.getId(),
                    saved.getImageUrl(),
                    saved.getS3Key(),
                    "사진을 저장했습니다."
            );
        } catch (Exception e) {
            // DB 저장 실패 시 S3 파일 삭제
            try {
                s3ObjectStorage.delete(putResult.key());
            } catch (RuntimeException ignored) {
                // 이미 업로드된 객체 정리 실패는 로깅은 공통 컴포넌트에서 처리됨
            }
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        }
    }

    @Transactional
    public void deleteImage(Long imageId, Long userId) {
        // 지우려는 이미지가 있는지
        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        // 본인이 맞는지
        if (!Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_DELETED);
        }

        // 신고 테이블이 map_image_id(FK)로 참조 중일 수 있어 먼저 참조를 끊는다.
        pictureReportRepository.detachMapImageByMapImageId(mapImage.getId());

        String s3Key = mapImage.getS3Key();
        mapImageRepository.delete(mapImage);
        // DB 제약 위반이 있으면 여기서 즉시 예외가 터지도록 flush
        mapImageRepository.flush();

        // 커밋 성공 후 S3 삭제(실패해도 DB 롤백은 불가하므로 로그만 남김)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    deleteFromS3(s3Key);
                } catch (RuntimeException exception) {
                    log.warn("Failed to delete S3 object after DB commit. key={}", s3Key, exception);
                }
            }
        });
    }

    // 삭제 메서드
    private void deleteFromS3(String s3Key) {
        try {
            s3ObjectStorage.delete(s3Key);
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }
    }

    private MapException toMapException(S3StorageException exception) {
        S3StorageError error = exception.getError();
        if (error == S3StorageError.NOT_CONFIGURED) {
            return new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }
        if (error == S3StorageError.CONNECTION_ERROR) {
            return new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
        return new MapException(MapErrorCode.DELETE_ERROR);
    }
}
