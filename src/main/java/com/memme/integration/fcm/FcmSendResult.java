package com.memme.integration.fcm;

import java.util.Objects;

public record FcmSendResult(Status status, String messageId, String errorCode) {

    public FcmSendResult {
        Objects.requireNonNull(status, "status");
    }

    public static FcmSendResult sent(String messageId) {
        return new FcmSendResult(Status.SENT, Objects.requireNonNull(messageId, "messageId"), null);
    }

    public static FcmSendResult retryableFailure(String errorCode) {
        return new FcmSendResult(Status.RETRYABLE_FAILURE, null, errorCode);
    }

    public static FcmSendResult invalidToken(String errorCode) {
        return new FcmSendResult(Status.INVALID_TOKEN, null, errorCode);
    }

    public static FcmSendResult permanentFailure(String errorCode) {
        return new FcmSendResult(Status.PERMANENT_FAILURE, null, errorCode);
    }

    public static FcmSendResult disabled() {
        return new FcmSendResult(Status.DISABLED, null, "FCM_DISABLED");
    }

    public enum Status {
        SENT,
        RETRYABLE_FAILURE,
        INVALID_TOKEN,
        PERMANENT_FAILURE,
        DISABLED
    }
}
