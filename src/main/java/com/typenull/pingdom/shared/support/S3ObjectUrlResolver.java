package com.typenull.pingdom.shared.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class S3ObjectUrlResolver {

    private final String publicBaseUrl;
    private final String bucket;
    private final String region;

    public S3ObjectUrlResolver(
            @Value("${spring.cloud.aws.s3.public-base-url:}") String publicBaseUrl,
            @Value("${spring.cloud.aws.s3.bucket:}") String bucket,
            @Value("${spring.cloud.aws.region.static:}") String region
    ) {
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.bucket = trimToNull(bucket);
        this.region = trimToNull(region);
    }

    public String resolve(String storedUrl, String s3Key) {
        if (!StringUtils.hasText(s3Key)) {
            return storedUrl;
        }
        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl + "/" + encodeKey(s3Key);
        }
        if (StringUtils.hasText(bucket) && StringUtils.hasText(region)) {
            return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + encodeKey(s3Key);
        }
        return storedUrl;
    }

    private String encodeKey(String s3Key) {
        return URLEncoder.encode(s3Key.trim(), StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    private String trimTrailingSlash(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
