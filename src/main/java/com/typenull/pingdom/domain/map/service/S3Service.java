package com.typenull.pingdom.domain.map.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.global.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final AmazonS3 amazonS3;
    private final AwsProperties awsProperties;
    private final MapImageRepository mapImageRepository;

    @Transactional
    public void upload(MultipartFile multipartFile) throws IOException {
        // 파일명 중복 방지 (UUID + 원본파일명)
        String s3FileName = UUID.randomUUID() + "-" + multipartFile.getOriginalFilename();

        // S3에 저장할 데이터
        ObjectMetadata objMeta = new ObjectMetadata();
        objMeta.setContentLength(multipartFile.getSize()); // 파일 사이즈, 최대치는 yml에서 조절
        objMeta.setContentType(multipartFile.getContentType()); // 파일 타입

        // 업로드할 오브젝트
        amazonS3.putObject(
                awsProperties.getS3().getBucket(),
                s3FileName,
                multipartFile.getInputStream(),
                objMeta
        );

        // 파일의 URL 저장
        MapImage mapImage = MapImage.builder()
                .ImageUrl(amazonS3.getUrl(awsProperties.getS3().getBucket(), s3FileName).toString())
                .build();

        mapImageRepository.save(mapImage);
    }
}