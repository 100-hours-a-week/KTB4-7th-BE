package com.memme.service.sales.upload;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * 매출 업로드 저장의 락 충돌 재시도 전 대기. 시험에서 대기를 없앨 수 있도록 분리했다.
 */
@Component
public class LockRetryBackoff {

    private static final long BASE_MILLIS = 200;

    public void pause(int attempt) {
        long millis = Math.round(BASE_MILLIS * attempt * ThreadLocalRandom.current().nextDouble(0.8, 1.2));
        try {
            Thread.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("매출 업로드 저장 재시도 대기가 중단됐습니다.", exception);
        }
    }
}
