package com.typenull.pingdom.shared.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * bucket 프로퍼티 조건이 맞을 때 동기 S3 client와 presigner를 생성한다.
 * 자격 증명은 AWS 기본 provider chain에서 얻고 region이 비어 있으면 ap-northeast-2를 사용한다.
 */
@Configuration
public class S3ClientConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.aws.s3.bucket")
    public S3Client s3Client(AwsRegionProperties awsRegionProperties) {
        String region = StringUtils.hasText(awsRegionProperties.region())
                ? awsRegionProperties.region()
                : "ap-northeast-2";

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.aws.s3.bucket")
    public S3Presigner s3Presigner(AwsRegionProperties awsRegionProperties) {
        String region = StringUtils.hasText(awsRegionProperties.region())
                ? awsRegionProperties.region()
                : "ap-northeast-2";
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
