package com.memme.service.noti;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class SalesUploadReminderSchedulerTest {

    @Test
    void 매일_알림_발송_Service를_호출한다() {
        SalesUploadReminderService reminderService = mock(SalesUploadReminderService.class);
        SalesUploadReminderScheduler scheduler = new SalesUploadReminderScheduler(reminderService);

        scheduler.sendDailyReminders();

        verify(reminderService).sendDailyReminders();
    }
}
