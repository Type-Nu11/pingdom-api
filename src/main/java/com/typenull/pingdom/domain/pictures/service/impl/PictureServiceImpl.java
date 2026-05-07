package com.typenull.pingdom.domain.pictures.service.impl;

import com.typenull.pingdom.domain.pictures.domain.Picture;
import com.typenull.pingdom.domain.pictures.dto.PictureUploadRequest;
import com.typenull.pingdom.domain.pictures.dto.PictureUploadResponse;
import com.typenull.pingdom.domain.pictures.repository.PictureRepository;
import com.typenull.pingdom.domain.pictures.service.PictureService;
import com.typenull.pingdom.global.s3.S3ObjectStorage;
import com.typenull.pingdom.global.s3.S3ObjectStorage.S3StorageException;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PictureServiceImpl implements PictureService {

    private final S3ObjectStorage s3ObjectStorage;
    private final PictureRepository pictureRepository;

    @Override
    @Transactional
    public PictureUploadResponse upload(PictureUploadRequest request) throws IOException {
        S3ObjectStorage.S3PutResult putResult;
        try {
            putResult = s3ObjectStorage.put(request.file(), "pictures");
        } catch (S3StorageException exception) {
            throw new IllegalStateException("S3 업로드에 실패했습니다.", exception);
        }

        Picture saved = pictureRepository.save(Picture.builder()
                .url(putResult.url())
                .s3Key(putResult.key())
                .createdAt(LocalDateTime.now())
                .build());

        return new PictureUploadResponse(saved.getId(), saved.getUrl(), saved.getS3Key());
    }
}
