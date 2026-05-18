package com.typenull.pingdom.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("fcm-key.json");

        if (!resource.exists()) {
            log.warn("Firebase 초기화 스킵: classpath에 fcm-key.json이 없습니다. (로컬 개발 환경에서는 정상일 수 있습니다)");
            return;
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(resource.getInputStream()))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
