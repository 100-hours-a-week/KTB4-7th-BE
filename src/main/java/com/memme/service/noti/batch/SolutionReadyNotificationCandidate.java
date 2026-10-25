package com.memme.service.noti.batch;

public record SolutionReadyNotificationCandidate(
        Long ownerUserId,
        Long bundleId,
        String notificationKey
) {
}
