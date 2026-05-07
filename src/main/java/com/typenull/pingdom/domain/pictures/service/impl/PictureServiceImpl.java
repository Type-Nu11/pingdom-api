package com.typenull.pingdom.domain.pictures.service.impl;

import com.typenull.pingdom.domain.pictures.domain.Picture;
import com.typenull.pingdom.domain.pictures.dto.PictureUploadRequest;
import com.typenull.pingdom.domain.pictures.dto.PictureUploadResponse;
import com.typenull.pingdom.domain.pictures.repository.PictureRepository;
import com.typenull.pingdom.domain.pictures.service.PictureService;
import com.typenull.pingdom.global.config.aws.AwsS3Properties;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class PictureServiceImpl implements PictureService {

    private final S3Client s3Client;
    private final AwsS3Properties awsS3Properties;
    private final PictureRepository pictureRepository;

    @Override
    @Transactional
    public PictureUploadResponse upload(PictureUploadRequest request) throws IOException {
        if (!StringUtils.hasText(awsS3Properties.bucket())) {
            throw new IllegalStateException("S3 bucket 설정이 필요합니다.");
        }

        String originalFilename = request.file().getOriginalFilename();
        String s3Key = "pictures/" + UUID.randomUUID() + "-" + (originalFilename != null ? originalFilename : "unnamed");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsS3Properties.bucket())
                .key(s3Key)
                .contentLength(request.file().getSize())
                .contentType(request.file().getContentType())
                .build();

        s3Client.putObject(
                putObjectRequest,
                RequestBody.fromInputStream(request.file().getInputStream(), request.file().getSize())
        );

        String url = String.format(
                "https://%s.s3.%s.amazonaws.com/%s",
                awsS3Properties.bucket(),
                s3Client.serviceClientConfiguration().region().id(),
                s3Key
        );

        Picture saved = pictureRepository.save(Picture.builder()
                .url(url)
                .s3Key(s3Key)
                .createdAt(LocalDateTime.now())
                .build());

        return new PictureUploadResponse(saved.getId(), saved.getUrl(), saved.getS3Key());
    }
}

