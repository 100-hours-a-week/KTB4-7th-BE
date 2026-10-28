package com.memme.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.memme.integration.fcm.DisabledFcmClient;
import com.memme.integration.fcm.FcmClient;
import com.memme.integration.fcm.FirebaseFcmClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FcmProperties.class)
public class FcmConfiguration {

    private static final String FIREBASE_APP_NAME = "memme-fcm";

    @Bean(destroyMethod = "delete")
    @Profile({"notification-batch", "local", "default"})
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    FirebaseApp fcmFirebaseApp(FcmProperties properties) {
        if (properties.getProjectId().isBlank()) {
            throw new IllegalStateException("FCM is enabled but FCM_PROJECT_ID is missing.");
        }
        if (properties.getServiceAccountJson().isBlank()) {
            throw new IllegalStateException("FCM is enabled but FCM_SERVICE_ACCOUNT_JSON is missing.");
        }

        try (ByteArrayInputStream credentials = new ByteArrayInputStream(
                properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setProjectId(properties.getProjectId())
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (IOException | RuntimeException ignored) {
            throw new IllegalStateException("Unable to initialize Firebase Admin SDK from configured credentials.");
        }
    }

    @Bean
    @Profile({"notification-batch", "local", "default"})
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(FirebaseApp fcmFirebaseApp) {
        return FirebaseMessaging.getInstance(fcmFirebaseApp);
    }

    @Bean
    @Profile({"notification-batch", "local", "default"})
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    FcmClient firebaseFcmClient(FirebaseMessaging firebaseMessaging) {
        return new FirebaseFcmClient(firebaseMessaging);
    }

    @Bean
    @Profile({"notification-batch", "local", "default"})
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
    FcmClient disabledFcmClient() {
        return new DisabledFcmClient();
    }

    @Bean
    @Profile("!notification-batch & !local & !default")
    FcmClient disabledFcmClientOutsideNotificationBatch() {
        return new DisabledFcmClient();
    }
}
