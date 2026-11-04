package com.memme.service.noti.fcm;

import com.memme.dto.noti.FcmPushMessage;
import java.util.Objects;

public class DisabledFcmClient implements FcmClient {

    @Override
    public FcmSendResult send(FcmPushMessage message) {
        Objects.requireNonNull(message, "message");
        return FcmSendResult.disabled();
    }
}
