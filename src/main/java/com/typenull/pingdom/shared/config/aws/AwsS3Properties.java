package com.typenull.pingdom.shared.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** S3 bucket 설정을 바인딩. 객체 저장소가 실제 작업 시 빈 bucket을 구성 누락으로 처리. */
@ConfigurationProperties(prefix = "spring.cloud.aws.s3")
public record AwsS3Properties(
        String bucket
) {
}

