package com.caregiver.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {
    private final String firebaseKeyPath;

    public FirebaseConfig(@Value("${firebase.key.path:}") String firebaseKeyPath) {
        this.firebaseKeyPath = firebaseKeyPath;
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${firebase.key.path:}')")
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            String firebaseKey = resolveFirebaseKey();
            if (!StringUtils.hasText(firebaseKey)) {
                throw new IllegalStateException(
                        "Missing Firebase credentials. Please set firebase.key.path.");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(
                            GoogleCredentials.fromStream(
                                    new ByteArrayInputStream(firebaseKey.getBytes(StandardCharsets.UTF_8))))
                    .build();
            FirebaseApp.initializeApp(options);
        }
        return FirebaseMessaging.getInstance();
    }

    private String resolveFirebaseKey() throws IOException {
        if (StringUtils.hasText(firebaseKeyPath)) {
            String content;
            if (firebaseKeyPath.startsWith("classpath:")) {
                String resourcePath = firebaseKeyPath.substring("classpath:".length());
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) return null;
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                content = Files.readString(Path.of(firebaseKeyPath), StandardCharsets.UTF_8);
            }
            if (StringUtils.hasText(content)) {
                return content;
            }
        }
        return null;
    }
}






