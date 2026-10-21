package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.entity.solution.SolutionRegenerationEntity;
import com.memme.entity.solution.SolutionRegenerationStatus;
import com.memme.entity.solution.SolutionType;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRegenerationRepository;
import com.memme.repository.solution.SolutionRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolutionRegenerationPersistenceService {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private final SolutionBundleRepository bundles;
    private final SolutionRepository cards;
    private final SolutionRegenerationRepository executions;

    public SolutionRegenerationPersistenceService(SolutionBundleRepository bundles,
            SolutionRepository cards, SolutionRegenerationRepository executions) {
        this.bundles = bundles;
        this.cards = cards;
        this.executions = executions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SolutionRegenerationReservation reserve(Long storeId, LocalDate date, Long expectedAnalysisId,
            String fingerprint, LocalDateTime now) {
        var bundle = bundles.findByStoreIdAndTargetDateForUpdate(storeId, date).orElse(null);
        if (bundle == null || bundle.getStatus() != SolutionBundleStatus.COMPLETED
                || !expectedAnalysisId.equals(bundle.getSalesAnalysisId())
                || cards.findAllBySolutionBundleIdOrderByRankNoAsc(bundle.getId()).isEmpty()) {
            return SolutionRegenerationReservation.rejected(SolutionRegenerationOutcome.SKIPPED);
        }
        List<SolutionRegenerationEntity> history = executions
                .findAllByStoreIdAndTargetDateOrderByStartedAtDesc(storeId, date);
        if (history.stream().anyMatch(e -> e.getStatus() == SolutionRegenerationStatus.COMPLETED
                && e.getSourceFingerprint().equals(fingerprint))) {
            return SolutionRegenerationReservation.rejected(SolutionRegenerationOutcome.ALREADY_COMPLETED);
        }
        for (var execution : history) {
            if (execution.getStatus() != SolutionRegenerationStatus.PROCESSING) continue;
            if (execution.getStartedAt().plus(TIMEOUT).isAfter(now)) {
                return SolutionRegenerationReservation.rejected(SolutionRegenerationOutcome.IN_PROGRESS);
            }
            execution.fail(now);
        }
        var execution = executions.findByStoreIdAndTargetDateAndSourceFingerprint(storeId, date, fingerprint)
                .orElse(null);
        if (execution != null && execution.getAttemptCount() >= MAX_ATTEMPTS) {
            return SolutionRegenerationReservation.rejected(SolutionRegenerationOutcome.RETRY_LIMIT_REACHED);
        }
        if (execution == null) execution = SolutionRegenerationEntity.start(storeId, date, fingerprint, now);
        else execution.restart(now);
        execution = executions.saveAndFlush(execution);
        return SolutionRegenerationReservation.acquired(execution.getId(), execution.getAttemptCount(), execution.getStartedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long storeId, LocalDate date, Long expectedAnalysisId, Long newAnalysisId,
            SolutionRegenerationReservation reservation, SalesSolutionGenerationResponse.Data candidate, LocalDateTime now) {
        validateCandidate(date, candidate);
        var bundle = bundles.findByStoreIdAndTargetDateForUpdate(storeId, date)
                .orElseThrow(() -> new IllegalStateException("solution bundle missing"));
        var execution = current(reservation);
        if (bundle.getStatus() != SolutionBundleStatus.COMPLETED
                || !expectedAnalysisId.equals(bundle.getSalesAnalysisId())) {
            throw new IllegalStateException("completed solution changed");
        }
        var previous = cards.findAllBySolutionBundleIdOrderByRankNoAsc(bundle.getId());
        if (previous.isEmpty()) throw new IllegalStateException("completed cards missing");
        int archiveRank = cards.findMaxRankNoBySolutionBundleId(bundle.getId()) + 1;
        for (var card : previous) card.archive(archiveRank++);
        cards.flush();
        for (var card : candidate.solutionCards()) {
            cards.save(SolutionEntity.create(storeId, newAnalysisId, bundle.getId(), SolutionType.DAILY,
                    card.title(), card.summaryText(), card.detailText(), card.evidence(), now, card.rankNo()));
        }
        bundle.replaceCompletedAnalysis(expectedAnalysisId, newAnalysisId);
        execution.complete(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(SolutionRegenerationReservation reservation, LocalDateTime now) {
        executions.findById(reservation.executionId())
                .filter(e -> e.getStatus() == SolutionRegenerationStatus.PROCESSING)
                .filter(e -> e.getAttemptCount() == reservation.attempt()
                        && e.getStartedAt().equals(reservation.startedAt()))
                .ifPresent(e -> e.fail(now));
    }

    private SolutionRegenerationEntity current(SolutionRegenerationReservation reservation) {
        var execution = executions.findById(reservation.executionId())
                .orElseThrow(() -> new IllegalStateException("regeneration execution missing"));
        if (execution.getStatus() != SolutionRegenerationStatus.PROCESSING
                || execution.getAttemptCount() != reservation.attempt()
                || !execution.getStartedAt().equals(reservation.startedAt())) {
            throw new IllegalStateException("regeneration attempt is stale");
        }
        return execution;
    }

    private void validateCandidate(LocalDate date, SalesSolutionGenerationResponse.Data candidate) {
        if (candidate == null || !date.equals(candidate.targetDate()) || candidate.solutionCards() == null
                || candidate.solutionCards().isEmpty() || candidate.solutionCards().size() > 3) {
            throw new IllegalArgumentException("invalid solution candidate");
        }
        var ranks = new HashSet<Integer>();
        for (var card : candidate.solutionCards()) {
            if (card == null || !ranks.add(card.rankNo()) || card.rankNo() < 1 || card.rankNo() > 3) {
                throw new IllegalArgumentException("invalid solution rank");
            }
        }
    }
}
