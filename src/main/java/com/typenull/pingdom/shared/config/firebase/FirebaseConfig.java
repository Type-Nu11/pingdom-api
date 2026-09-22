package com.typenull.pingdom.shared.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * FCM 활성 시 파일의 서비스 계정 자격 증명을 읽어 기본 FirebaseApp을 초기화하거나 기존 앱을 재사용한다.
 * 기존 앱이 있어도 먼저 키 파일을 읽으므로 파일 접근·파싱 실패는 빈 생성 실패로 이어진다.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${fcm.key-path}")
    private String fcmKeyPath;

    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "true", matchIfMissing = true)
    public FirebaseApp firebaseApp() throws IOException {
        try (InputStream stream = new FileInputStream(fcmKeyPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.initializeApp(options);
            }
            return FirebaseApp.getInstance();
        }
    }
}
