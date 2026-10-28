package com.memme.integration.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.memme.config.FcmConfiguration;
import com.memme.config.FcmProperties;
import com.memme.dto.noti.FcmPushMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FcmConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FcmConfiguration.class);

    @Test
    void 비활성화된_로컬_FCM은_자격_증명_없이_시작하고_비활성_클라이언트를_제공한다() {
        contextRunner
                .withPropertyValues("app.fcm.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FcmClient.class);
                    assertThat(context).doesNotHaveBean(FirebaseMessaging.class);
                    FcmPushMessage message = new FcmPushMessage(
                            "registered-token",
                            new FcmPushMessage.NotificationPayload("제목", "내용"),
                            Map.of("notificationId", "1", "type", "SOLUTION_READY"));
                    assertThat(context.getBean(FcmClient.class).send(message).status())
                            .isEqualTo(FcmSendResult.Status.DISABLED);
                });
    }

    @Test
    void 활성화한_FCM에_서비스_계정_정보가_없으면_기동을_거부한다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("notification-batch"))
                .withPropertyValues("app.fcm.enabled=true", "app.fcm.project-id=test-project")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("FCM")
                            .hasMessageNotContaining("private_key");
                });
    }

    @Test
    void 웹_태스크는_FCM을_활성화해도_자격_증명_없이_시작하고_발송하지_않는다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("notification-web"))
                .withPropertyValues("app.fcm.enabled=true", "app.fcm.project-id=test-project")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FcmClient.class);
                    assertThat(context).doesNotHaveBean(FirebaseMessaging.class);
                    FcmPushMessage message = new FcmPushMessage(
                            "registered-token",
                            new FcmPushMessage.NotificationPayload("제목", "내용"),
                            Map.of("notificationId", "1", "type", "SOLUTION_READY"));
                    assertThat(context.getBean(FcmClient.class).send(message).status())
                            .isEqualTo(FcmSendResult.Status.DISABLED);
                });
    }

    @Test
    void 잘못된_자격_증명_문자열은_기동_오류에_노출하지_않는다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("notification-batch"))
                .withPropertyValues(
                        "app.fcm.enabled=true",
                        "app.fcm.project-id=test-project",
                        "app.fcm.service-account-json={\"private_key\":\"private-value\"}")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageNotContaining("private-value")
                            .hasMessageNotContaining("private_key");
                });
    }

    @Test
    void 자격_증명_속성의_문자열_표현은_비밀값을_가린다() {
        FcmProperties properties = new FcmProperties();
        properties.setProjectId("test-project");
        properties.setServiceAccountJson("private-value");

        assertThat(properties.toString())
                .contains("<redacted>")
                .doesNotContain("private-value");
    }
}
