package com.memme.service.noti;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SalesUploadReminderScheduler {

    private final SalesUploadReminderService reminderService;

    public SalesUploadReminderScheduler(SalesUploadReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "${SALES_UPLOAD_REMINDER_CRON:0 0 8 * * *}", zone = "Asia/Seoul")
    public void sendDailyReminders() {
        reminderService.sendDailyReminders();
    }
}
