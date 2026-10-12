package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RankingChangeNotificationSchedulerTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Test
    void 매일_오전_8시_KST에_전날을_기준일로_Service를_한번_호출한다() {
        RankingChangeNotificationService service = mock(RankingChangeNotificationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T15:30:00Z"), ZoneId.of("UTC"));
        RankingChangeNotificationScheduler scheduler = new RankingChangeNotificationScheduler(service, clock);

        scheduler.createDailyRankingChangeNotifications();

        verify(service).createDailyRankingChangeNotifications(LocalDate.of(2026, 10, 11));
        verifyNoMoreInteractions(service);
    }

    @Test
    void 매일_오전_8시_KST_스케줄로_등록된다() throws NoSuchMethodException {
        Method method = RankingChangeNotificationScheduler.class
                .getMethod("createDailyRankingChangeNotifications");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${RANKING_CHANGE_NOTIFICATION_CRON:0 0 8 * * *}");
        assertThat(scheduled.zone()).isEqualTo(KOREA_ZONE.getId());
    }
}
