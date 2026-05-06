package com.typenull.pingdom.global.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.typenull.pingdom.global.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final AwsProperties awsProperties;

    @Bean
    public AmazonS3 amazonS3() {
        //yml에서 불러온거
        BasicAWSCredentials credentials = new BasicAWSCredentials(
                awsProperties.getCredentials().getAccessKey(),
                awsProperties.getCredentials().getSecretKey()
        );

        //불러온 정보로 클라이언트 만들기
        return AmazonS3ClientBuilder.standard()
                .withRegion(awsProperties.getRegion().getStaticRegion())
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }
}