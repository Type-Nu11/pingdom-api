package com.typenull.pingdom.global.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

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
}
