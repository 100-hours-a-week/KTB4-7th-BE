package com.memme.service.sales.outbox;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class SalesOutboxTelemetry {
    private final Map<String, LongAdder> outcomes = new ConcurrentHashMap<>();
    private final LongAdder elapsedNanos = new LongAdder();
    public void record(String outcome, long nanos) {
        if (!java.util.Set.of("DONE", "READY", "DEAD", "OWNERSHIP_LOST", "UNRECORDED", "REJECTED").contains(outcome)) {
            throw new IllegalArgumentException("unknown outbox outcome");
        }
        outcomes.computeIfAbsent(outcome, key -> new LongAdder()).increment();
        elapsedNanos.add(nanos);
    }
    public Map<String, Long> snapshot() {
        var result = new java.util.TreeMap<String, Long>();
        outcomes.forEach((key, value) -> result.put(key, value.sum()));
        result.put("elapsed_millis", elapsedNanos.sum() / 1_000_000);
        return Map.copyOf(result);
    }
}
