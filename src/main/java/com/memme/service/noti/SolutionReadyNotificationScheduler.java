package com.memme.service.noti;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 솔루션은 업로드 직후 또는 매일 00시 배치로 생성되지만,
 * "오늘의 솔루션이 도착했습니다" 알림은 생성 시점과 무관하게
 * 매일 08시에 한 번만 발송한다.
 */
@Component
public class SolutionReadyNotificationScheduler {

    private final SolutionReadyNotificationService notificationService;

    public SolutionReadyNotificationScheduler(SolutionReadyNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${SOLUTION_READY_NOTIFICATION_CRON:0 0 8 * * *}", zone = "Asia/Seoul")
    public void notifyTodaysSolutions() {
        notificationService.notifyTodaysSolutions();
    }
}
