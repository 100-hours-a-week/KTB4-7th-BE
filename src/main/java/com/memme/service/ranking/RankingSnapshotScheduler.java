package com.memme.service.ranking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RankingSnapshotScheduler {

    private final RankingSnapshotRecalculationService recalculationService;

    public RankingSnapshotScheduler(RankingSnapshotRecalculationService recalculationService) {
        this.recalculationService = recalculationService;
    }

    @Scheduled(cron = "${RANKING_SNAPSHOT_MONTHLY_CRON:0 0 0 1 * *}", zone = "Asia/Seoul")
    public void recalculateMonthlySnapshot() {
        recalculationService.recalculateCurrentPeriod();
    }
}
