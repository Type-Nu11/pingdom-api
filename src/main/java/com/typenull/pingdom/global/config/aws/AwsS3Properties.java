package com.typenull.pingdom.global.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.cloud.aws.s3")
public record AwsS3Properties(
        String bucket
) {
}

