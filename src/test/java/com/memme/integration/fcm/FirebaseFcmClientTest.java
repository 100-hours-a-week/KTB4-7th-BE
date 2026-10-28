package com.memme.integration.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.memme.dto.noti.FcmPushMessage;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FirebaseFcmClientTest {

    private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
    private final FirebaseFcmClient client = new FirebaseFcmClient(firebaseMessaging);

    @Test
    @SuppressWarnings("unchecked")
    void 성공하면_FCM_메시지를_전달하고_메시지_ID를_반환한다() throws Exception {
        when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/memme/messages/123");

        FcmSendResult result = client.send(message());

        assertThat(result.status()).isEqualTo(FcmSendResult.Status.SENT);
        assertThat(result.messageId()).isEqualTo("projects/memme/messages/123");
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(firebaseMessaging).send(captor.capture());
        Message sent = captor.getValue();
        assertThat(field(sent, "token")).isEqualTo("registered-token");
        Object notification = field(sent, "notification");
        assertThat(field(notification, "title")).isEqualTo("알림 제목");
        assertThat(field(notification, "body")).isEqualTo("알림 내용");
        assertThat(field(sent, "data"))
                .isInstanceOf(Map.class);
        assertThat((Map<String, String>) field(sent, "data"))
                .containsEntry("notificationId", "42")
                .containsEntry("type", "SOLUTION_READY");
    }

    @Test
    void UNREGISTERED는_무효_토큰으로_분류한다() throws Exception {
        FirebaseMessagingException exception = messagingException(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        FcmSendResult result = client.send(message());

        assertThat(result.status()).isEqualTo(FcmSendResult.Status.INVALID_TOKEN);
        assertThat(result.errorCode()).isEqualTo("UNREGISTERED");
    }

    @Test
    void 일시적인_FCM_오류는_재시도_대상으로_분류한다() throws Exception {
        FirebaseMessagingException exception = messagingException(MessagingErrorCode.UNAVAILABLE);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        FcmSendResult result = client.send(message());

        assertThat(result.status()).isEqualTo(FcmSendResult.Status.RETRYABLE_FAILURE);
        assertThat(result.errorCode()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void 잘못된_페이로드는_무효_토큰이_아닌_영구_오류로_분류한다() throws Exception {
        FirebaseMessagingException exception = messagingException(MessagingErrorCode.INVALID_ARGUMENT);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        FcmSendResult result = client.send(message());

        assertThat(result.status()).isEqualTo(FcmSendResult.Status.PERMANENT_FAILURE);
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    private FcmPushMessage message() {
        return new FcmPushMessage(
                "registered-token",
                new FcmPushMessage.NotificationPayload("알림 제목", "알림 내용"),
                Map.of("notificationId", "42", "type", "SOLUTION_READY"));
    }

    private FirebaseMessagingException messagingException(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(errorCode);
        return exception;
    }

    private Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
