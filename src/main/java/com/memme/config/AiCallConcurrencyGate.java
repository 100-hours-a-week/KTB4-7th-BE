package com.memme.config;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AiCallConcurrencyGate {

    private final Semaphore permit = new Semaphore(1, true);

    public <T> T call(Supplier<T> operation) {
        try {
            permit.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI call interrupted while waiting for the shared permit", exception);
        }
        try {
            return operation.get();
        } finally {
            permit.release();
        }
    }
}
