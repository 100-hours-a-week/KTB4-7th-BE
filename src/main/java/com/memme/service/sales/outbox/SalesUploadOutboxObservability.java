package com.memme.service.sales.outbox;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SalesUploadOutboxObservability {
    private static final Logger log = LoggerFactory.getLogger(SalesUploadOutboxObservability.class);
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final SalesUploadOutboxCoordinator coordinator;
    public SalesUploadOutboxObservability(JdbcTemplate jdbc, SalesUploadOutboxCoordinator coordinator,
            @Value("${app.sales.outbox.enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.coordinator = coordinator; this.enabled = enabled;
    }
    public Map<String, Long> snapshot() {
        var values = new java.util.TreeMap<String, Long>();
        jdbc.query("SELECT status, COUNT(*) AS jobs FROM sales_upload_outbox GROUP BY status", result -> {
            values.put(result.getString("status"), result.getLong("jobs"));
        });
        values.put("oldest_pending_seconds", jdbc.queryForObject("""
                SELECT COALESCE(MAX(TIMESTAMPDIFF(SECOND,created_at,UTC_TIMESTAMP(6))),0)
                FROM sales_upload_outbox WHERE status IN ('READY','RUNNING')
                """, Long.class));
        values.put("expired_running", jdbc.queryForObject("""
                SELECT COUNT(*) FROM sales_upload_outbox WHERE status='RUNNING' AND lease_until<=UTC_TIMESTAMP(6)
                """, Long.class));
        values.put("retry_attempts", jdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(attempt_count-1,0)),0) FROM sales_upload_outbox", Long.class));
        values.put("lease_recoveries_since_start", coordinator.leaseRecoveries());
        return Map.copyOf(values);
    }
    @Scheduled(fixedDelay = 30000)
    public void report() {
        if (!enabled) return;
        try { log.info("sales_outbox_queue={}", snapshot()); }
        catch (RuntimeException failure) { log.warn("Outbox queue observation unavailable", failure); }
    }
}
