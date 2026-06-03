package com.typenull.pingdom.shared.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.cloud.aws")
public record AwsRegionProperties(
        String region
) {
}

