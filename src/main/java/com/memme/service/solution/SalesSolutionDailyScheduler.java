package com.memme.service.solution;

import java.time.Clock;
import java.time.LocalDate;

import com.memme.entity.store.StoreStatus;
import com.memme.repository.store.StoreRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SalesSolutionDailyScheduler {

    private final StoreRepository storeRepository;
    private final SalesSolutionGenerationService generationService;
    private final Clock clock;

    public SalesSolutionDailyScheduler(
            StoreRepository storeRepository,
            SalesSolutionGenerationService generationService,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.generationService = generationService;
        this.clock = clock;
    }

    @Scheduled(cron = "${SOLUTION_DAILY_CRON:0 0 0 * * *}", zone = "Asia/Seoul")
    public void generateDailySolutions() {
        LocalDate targetDate = LocalDate.now(clock);
        storeRepository.findAllByStatus(StoreStatus.ACTIVE)
                .forEach(store -> generationService.generateScheduled(store.getId(), targetDate));
    }
}
