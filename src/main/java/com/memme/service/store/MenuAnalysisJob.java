package com.memme.service.store;

import com.memme.entity.store.MenuImage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class MenuAnalysisJob {

    private static final Logger log = LoggerFactory.getLogger(MenuAnalysisJob.class);

    private final MenuAnalysisLifecycleService lifecycle;
    private final MenuImageStorage storage;
    private final MenuRecognitionAiClient aiClient;

    public MenuAnalysisJob(MenuAnalysisLifecycleService lifecycle, MenuImageStorage storage,
            MenuRecognitionAiClient aiClient) {
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.aiClient = aiClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(MenuBatchAccepted event) {
        try {
            List<MenuImage> images = lifecycle.begin(event.storeId(), event.batchId());
            List<MenuRecognitionAiClient.Image> requests = images.stream()
                    .map(image -> new MenuRecognitionAiClient.Image(image.getId(),
                            storage.signedReadUrl(image.getStorageKey()), image.getImageOrder()))
                    .toList();
            lifecycle.complete(event.storeId(), event.batchId(),
                    aiClient.recognize(event.storeId(), event.batchId(), requests));
        } catch (MenuRecognitionFailure exception) {
            lifecycle.fail(event.storeId(), event.batchId(), exception.reason());
        } catch (Exception exception) {
            log.warn("메뉴판 AI 분석 실패: batchId={}, type={}",
                    event.batchId(), exception.getClass().getSimpleName());
            lifecycle.fail(event.storeId(), event.batchId(), "AI_PROCESSING_ERROR");
        }
    }
}
