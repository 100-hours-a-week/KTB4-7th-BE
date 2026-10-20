package com.memme.service.sales;

import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import com.memme.repository.sales.SeoulDistrictQuarterImportRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.seoul-district-import", name = "enabled", havingValue = "true")
public class SeoulDistrictQuarterCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeoulDistrictQuarterCollectionScheduler.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String CAFE_INDUSTRY_CODE = "CS100010";
    private static final int CANDIDATE_QUARTERS = 3;

    private final SeoulDistrictQuarterCollectionService collection;
    private final SeoulDistrictQuarterImportRepository imports;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public SeoulDistrictQuarterCollectionScheduler(SeoulDistrictQuarterCollectionService collection,
                                                   SeoulDistrictQuarterImportRepository imports,
                                                   Clock clock) {
        this.collection = collection;
        this.imports = imports;
        this.clock = clock;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartup() {
        collectLatest("startup");
    }

    @Scheduled(cron = "${SEOUL_DISTRICT_IMPORT_CRON:0 0 12 * * *}", zone = "Asia/Seoul")
    public void collectDaily() {
        collectLatest("daily");
    }

    private void collectLatest(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("Seoul district quarter collection skipped: another run is active (trigger={})", trigger);
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock.withZone(KOREA));
            int currentQuarterFirstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
            YearMonth lastClosedQuarter = YearMonth.of(today.getYear(), currentQuarterFirstMonth).minusMonths(1);
            for (int offset = 0; offset < CANDIDATE_QUARTERS; offset++) {
                YearMonth candidate = lastClosedQuarter.minusMonths(3L * offset);
                String basisQuarter = candidate.getYear() + "Q" + ((candidate.getMonthValue() - 1) / 3 + 1);
                try {
                    var existing = imports.findByBasisQuarterAndIndustryCode(basisQuarter, CAFE_INDUSTRY_CODE);
                    if (existing.isPresent() && existing.get().getStatus() == SeoulDistrictQuarterImportStatus.COMPLETED) {
                        log.info("Seoul district quarter already collected: trigger={}, quarter={}", trigger, basisQuarter);
                        return;
                    }
                    if (existing.isPresent() && existing.get().getStatus() == SeoulDistrictQuarterImportStatus.PROCESSING) {
                        log.warn("Seoul district quarter still processing: trigger={}, quarter={}", trigger, basisQuarter);
                        continue;
                    }
                    if (collection.collect(basisQuarter)) {
                        log.info("Seoul district quarter collected: trigger={}, quarter={}", trigger, basisQuarter);
                        return;
                    }
                } catch (RuntimeException exception) {
                    log.warn("Seoul district quarter collection failed: trigger={}, quarter={}, cause={}",
                            trigger, basisQuarter, exception.getClass().getSimpleName());
                }
            }
            log.info("No complete Seoul district quarter available: trigger={}", trigger);
        } finally {
            running.set(false);
        }
    }
}
