package com.memme.integration.fcm;

import com.memme.dto.noti.FcmPushMessage;
import java.util.Objects;

public class DisabledFcmClient implements FcmClient {

    @Override
    public FcmSendResult send(FcmPushMessage message) {
        Objects.requireNonNull(message, "message");
        return FcmSendResult.disabled();
    }
}
