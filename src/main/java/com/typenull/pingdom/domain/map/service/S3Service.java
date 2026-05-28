package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.dto.MapImageResponse;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3ObjectStorage s3ObjectStorage;
    private final MapImageRepository mapImageRepository;
    private final PictureReportRepository pictureReportRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    public MapImageResponse uploadImage(ImageUploadRequest request, long userId) {

        String username = userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        S3ObjectStorage.S3PutResult putResult;
        try {
            putResult = s3ObjectStorage.put(request.file(), "map");
        } catch (IOException exception) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }

        // 파일의 URL 저장
        try {
            MapImage mapImage = MapImage.builder()
                    .imageUrl(putResult.url())
                    .s3Key(putResult.key())
                    .title(request.title())
                    .description(request.description())
                    .userId(userId)
                    .username(username)
                    .build();

            savePost(mapImage);

            return new MapImageResponse(mapImage.getId(), "게시글을 저장했습니다.");
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

    public MapImageResponse deleteImage(Long imageId, Long userId) {
        // 지우려는 이미지가 있는지
        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        // 본인이 맞는지
        if (!Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_DELETED);
        }

        String s3Key = mapImage.getS3Key();
        deleteFromS3(s3Key);
        deletePostRecord(mapImage);

        return new MapImageResponse(imageId, "게시글을 삭제했습니다");
    }

    private void savePost(MapImage mapImage) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> mapImageRepository.saveAndFlush(mapImage));
    }

    private void deletePostRecord(MapImage mapImage) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            pictureReportRepository.detachMapImageByMapImageId(mapImage.getId());
            mapImageRepository.delete(mapImage);
        });
    }

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
