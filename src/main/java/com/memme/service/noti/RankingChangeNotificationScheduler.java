package com.memme.service.noti;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RankingChangeNotificationScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingChangeNotificationService rankingChangeNotificationService;
    private final Clock clock;

    public RankingChangeNotificationScheduler(
            RankingChangeNotificationService rankingChangeNotificationService,
            Clock clock
    ) {
        this.rankingChangeNotificationService = rankingChangeNotificationService;
        this.clock = clock;
    }

    @Scheduled(cron = "${RANKING_CHANGE_NOTIFICATION_CRON:0 0 8 * * *}", zone = "Asia/Seoul")
    public void createDailyRankingChangeNotifications() {
        LocalDate baseDate = LocalDate.now(clock.withZone(KOREA_ZONE)).minusDays(1);
        rankingChangeNotificationService.createDailyRankingChangeNotifications(baseDate);
    }
}
