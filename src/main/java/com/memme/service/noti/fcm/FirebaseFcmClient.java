package com.memme.service.noti.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.memme.dto.noti.FcmPushMessage;
import java.util.Objects;

public class FirebaseFcmClient implements FcmClient {

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseFcmClient(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = Objects.requireNonNull(firebaseMessaging, "firebaseMessaging");
    }

    @Override
    @SuppressWarnings("deprecation")
    public FcmSendResult send(FcmPushMessage pushMessage) {
        Objects.requireNonNull(pushMessage, "pushMessage");
        Message message = Message.builder()
                .setToken(pushMessage.token())
                .setNotification(Notification.builder()
                        .setTitle(pushMessage.notification().title())
                        .setBody(pushMessage.notification().body())
                        .build())
                .putAllData(pushMessage.data())
                .build();

        try {
            return FcmSendResult.sent(firebaseMessaging.send(message));
        } catch (FirebaseMessagingException exception) {
            return mapFailure(exception.getMessagingErrorCode());
        }
    }

    private FcmSendResult mapFailure(MessagingErrorCode errorCode) {
        if (errorCode == null) {
            return FcmSendResult.permanentFailure("UNKNOWN_FCM_ERROR");
        }
        return switch (errorCode) {
            case UNREGISTERED -> FcmSendResult.invalidToken(errorCode.name());
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> FcmSendResult.retryableFailure(errorCode.name());
            default -> FcmSendResult.permanentFailure(errorCode.name());
        };
    }
}
