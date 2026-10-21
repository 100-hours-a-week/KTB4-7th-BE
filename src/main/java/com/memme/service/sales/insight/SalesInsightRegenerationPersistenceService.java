package com.memme.service.sales.insight;

import com.memme.entity.sales.SalesAiInsightRegenerationEntity;
import com.memme.entity.sales.SalesAiInsightRegenerationStatus;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.repository.sales.SalesAiInsightRegenerationRepository;
import com.memme.repository.sales.SalesAiInsightRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesInsightRegenerationPersistenceService {
    static final int MAX_ATTEMPTS = 3;
    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private final SalesAiInsightRepository insights;
    private final SalesAiInsightRegenerationRepository executions;

    public SalesInsightRegenerationPersistenceService(SalesAiInsightRepository insights,
            SalesAiInsightRegenerationRepository executions) {
        this.insights = insights;
        this.executions = executions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SalesInsightRegenerationReservation reserve(Long storeId, YearMonth month, Long expectedAnalysisId,
            String fingerprint, LocalDateTime now) {
        var insight = insights.findByStoreIdAndTargetMonthForUpdate(storeId, month.atDay(1)).orElse(null);
        if (insight == null || insight.getStatus() != SalesAiInsightStatus.COMPLETED
                || !expectedAnalysisId.equals(insight.getSalesAnalysisId())) {
            return SalesInsightRegenerationReservation.rejected(SalesInsightRegenerationOutcome.SKIPPED);
        }
        List<SalesAiInsightRegenerationEntity> history = executions
                .findAllByStoreIdAndTargetMonthOrderByStartedAtDesc(storeId, month.atDay(1));
        if (history.stream().anyMatch(e -> e.getStatus() == SalesAiInsightRegenerationStatus.COMPLETED
                && e.getSourceFingerprint().equals(fingerprint))) {
            return SalesInsightRegenerationReservation.rejected(SalesInsightRegenerationOutcome.ALREADY_COMPLETED);
        }
        for (var execution : history) {
            if (execution.getStatus() != SalesAiInsightRegenerationStatus.PROCESSING) continue;
            if (execution.getStartedAt().plus(TIMEOUT).isAfter(now)) {
                return SalesInsightRegenerationReservation.rejected(SalesInsightRegenerationOutcome.IN_PROGRESS);
            }
            execution.fail(now);
        }
        var execution = executions.findByStoreIdAndTargetMonthAndSourceFingerprint(
                storeId, month.atDay(1), fingerprint).orElse(null);
        if (execution != null && execution.getAttemptCount() >= MAX_ATTEMPTS) {
            return SalesInsightRegenerationReservation.rejected(SalesInsightRegenerationOutcome.RETRY_LIMIT_REACHED);
        }
        if (execution == null) execution = SalesAiInsightRegenerationEntity.start(storeId, month, fingerprint, now);
        else execution.restart(now);
        execution = executions.saveAndFlush(execution);
        return SalesInsightRegenerationReservation.acquired(execution.getId(), execution.getAttemptCount(), execution.getStartedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long storeId, YearMonth month, Long expectedAnalysisId, Long newAnalysisId,
            SalesInsightRegenerationReservation reservation, List<String> candidate, LocalDateTime now) {
        validate(candidate);
        var insight = insights.findByStoreIdAndTargetMonthForUpdate(storeId, month.atDay(1))
                .orElseThrow(() -> new IllegalStateException("completed insight missing"));
        var execution = executions.findById(reservation.executionId())
                .orElseThrow(() -> new IllegalStateException("regeneration execution missing"));
        if (execution.getStatus() != SalesAiInsightRegenerationStatus.PROCESSING
                || execution.getAttemptCount() != reservation.attempt()
                || !execution.getStartedAt().equals(reservation.startedAt())) {
            throw new IllegalStateException("regeneration attempt is stale");
        }
        insight.replaceCompleted(expectedAnalysisId, newAnalysisId, candidate, now);
        execution.complete(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(SalesInsightRegenerationReservation reservation, LocalDateTime now) {
        executions.findById(reservation.executionId())
                .filter(e -> e.getStatus() == SalesAiInsightRegenerationStatus.PROCESSING)
                .filter(e -> e.getAttemptCount() == reservation.attempt()
                        && e.getStartedAt().equals(reservation.startedAt()))
                .ifPresent(e -> e.fail(now));
    }

    private void validate(List<String> candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.size() > 3) {
            throw new IllegalArgumentException("invalid insight candidate");
        }
        var unique = new HashSet<String>();
        for (String sentence : candidate) {
            if (sentence == null || sentence.isBlank() || sentence.length() > 100 || !unique.add(sentence)) {
                throw new IllegalArgumentException("invalid insight sentence");
            }
        }
    }
}
