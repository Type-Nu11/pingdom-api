package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.global.s3.S3ObjectStorage;
import com.typenull.pingdom.global.s3.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.global.s3.S3ObjectStorage.S3StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3ObjectStorage s3ObjectStorage;
    private final MapImageRepository mapImageRepository;

    @Transactional
    public void uploadImage(ImageUploadRequest request, long userId) throws IOException {
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

            mapImageRepository.save(mapImage);
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
        if (!mapImage.getUserId().equals(userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_DELETED);
        }

        deleteFromS3(mapImage.getS3Key());
        mapImageRepository.delete(mapImage);
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
