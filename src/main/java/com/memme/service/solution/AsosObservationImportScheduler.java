package com.memme.service.solution;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.asos-import", name = "enabled", havingValue = "true")
public class AsosObservationImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(AsosObservationImportScheduler.class);

    private final AsosObservationImportService importService;
    private final AtomicBoolean running = new AtomicBoolean();

    public AsosObservationImportScheduler(AsosObservationImportService importService) {
        this.importService = importService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        importObservations("startup");
    }

    @Scheduled(cron = "${ASOS_OBSERVATION_IMPORT_CRON:0 0 12 * * *}", zone = "Asia/Seoul")
    public void importDaily() {
        importObservations("daily");
    }

    private void importObservations(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("ASOS observation import skipped: another run is active (trigger={})", trigger);
            return;
        }
        try {
            int inserted = importService.importForActiveStores();
            log.info("ASOS observation import completed: trigger={}, inserted={}", trigger, inserted);
        } catch (RuntimeException exception) {
            log.error("ASOS observation import failed: trigger={}, cause={}",
                    trigger, exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
