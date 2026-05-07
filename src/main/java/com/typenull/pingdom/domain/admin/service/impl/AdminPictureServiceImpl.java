package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import com.typenull.pingdom.domain.pictures.domain.Picture;
import com.typenull.pingdom.domain.pictures.repository.PictureRepository;
import com.typenull.pingdom.global.config.aws.AwsS3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class AdminPictureServiceImpl implements AdminPictureService {

    private final PictureRepository pictureRepository;
    private final S3Client s3Client;
    private final AwsS3Properties awsS3Properties;

    @Override
    @Transactional
    public void deletePicture(Long pictureId) {
        Picture picture = pictureRepository.findById(pictureId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PICTURE_NOT_FOUND));

        if (StringUtils.hasText(picture.getS3Key()) && StringUtils.hasText(awsS3Properties.bucket())) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(picture.getS3Key())
                        .build());
            } catch (S3Exception exception) {
                throw new AdminException(AdminErrorCode.PICTURE_DELETE_FAILED);
            }
        }

        pictureRepository.delete(picture);
    }
}

