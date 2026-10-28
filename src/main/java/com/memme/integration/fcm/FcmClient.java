package com.memme.integration.fcm;

import com.memme.dto.noti.FcmPushMessage;

public interface FcmClient {

    FcmSendResult send(FcmPushMessage message);
}
