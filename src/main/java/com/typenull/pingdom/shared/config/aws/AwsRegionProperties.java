package com.typenull.pingdom.shared.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AWS client에 사용할 region이다. 공백 또는 미설정이면 S3ClientConfig가 서울 region을 사용한다. */
@ConfigurationProperties(prefix = "spring.cloud.aws")
public record AwsRegionProperties(
        String region
) {
}

