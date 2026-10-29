package com.memme.service.noti.fcm;

import com.memme.dto.noti.FcmPushMessage;

public interface FcmClient {

    FcmSendResult send(FcmPushMessage message);
}
