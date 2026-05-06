package com.typenull.pingdom.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cloud.aws")
@Getter
@Setter
public class AwsProperties {
    private S3 s3 = new S3();
    private Region region = new Region();
    private Credentials credentials = new Credentials();

    @Getter
    @Setter
    public static class S3 {
        private String bucket;
    }

    @Getter
    @Setter
    public static class Region {
        private String staticRegion;
    }

    @Getter
    @Setter
    public static class Credentials {
        private String accessKey;
        private String secretKey;
    }

}
