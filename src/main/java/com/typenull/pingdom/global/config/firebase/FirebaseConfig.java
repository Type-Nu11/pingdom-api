package com.typenull.pingdom.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void init() throws IOException {
        String fcmKeyJson = System.getenv("FCM_KEY_JSON");
        if (fcmKeyJson == null || fcmKeyJson.isEmpty()) {
            throw new IllegalStateException("FCM_KEY_JSON 환경변수가 없습니다.");
        }
        fcmKeyJson = fcmKeyJson.replace("\\n", "\n");
        InputStream stream = new ByteArrayInputStream(fcmKeyJson.getBytes(StandardCharsets.UTF_8));
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build();
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
