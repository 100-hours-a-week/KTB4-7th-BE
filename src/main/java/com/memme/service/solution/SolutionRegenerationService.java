package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationStatus;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.solution.SolutionBundleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SolutionRegenerationService {
    private static final Logger log = LoggerFactory.getLogger(SolutionRegenerationService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final SolutionBundleRepository bundles;
    private final SalesSolutionGenerationContextResolver contexts;
    private final SalesSolutionAiClient client;
    private final SolutionWeatherContextService weather;
    private final SolutionRegenerationPersistenceService persistence;
    private final SolutionRegenerationSourceFingerprint fingerprints;
    private final Clock clock;

    public SolutionRegenerationService(SolutionBundleRepository bundles,
            SalesSolutionGenerationContextResolver contexts, SalesSolutionAiClient client,
            SolutionWeatherContextService weather, SolutionRegenerationPersistenceService persistence,
            SolutionRegenerationSourceFingerprint fingerprints, Clock clock) {
        this.bundles = bundles;
        this.contexts = contexts;
        this.client = client;
        this.weather = weather;
        this.persistence = persistence;
        this.fingerprints = fingerprints;
        this.clock = clock;
    }

    public SolutionRegenerationOutcome regenerate(Long storeId, LocalDate date) {
        if (!date.equals(LocalDate.now(clock.withZone(KOREA)))) return SolutionRegenerationOutcome.SKIPPED;
        var existing = bundles.findByStoreIdAndTargetDate(storeId, date).orElse(null);
        if (existing == null || existing.getStatus() != SolutionBundleStatus.COMPLETED) {
            return SolutionRegenerationOutcome.SKIPPED;
        }
        var source = source(storeId, date);
        if (source == null) return SolutionRegenerationOutcome.SKIPPED;
        Long oldAnalysisId = existing.getSalesAnalysisId();
        var reservation = persistence.reserve(storeId, date, oldAnalysisId, source.fingerprint(), now());
        if (reservation.outcome() != SolutionRegenerationOutcome.RESERVED) return reservation.outcome();
        try {
            var request = new SalesSolutionGenerationRequest(storeId, null, date, "SCHEDULED", source.metrics());
            var response = client.generate(request);
            if (response == null || response.status() != SalesSolutionGenerationStatus.COMPLETED
                    || response.data() == null) {
                throw new IllegalStateException("AI solution candidate incomplete");
            }
            if (!date.equals(LocalDate.now(clock.withZone(KOREA)))) {
                throw new IllegalStateException("solution target date is no longer today");
            }
            var current = source(storeId, date);
            if (current == null || !source.fingerprint().equals(current.fingerprint())) {
                throw new IllegalStateException("solution source changed during generation");
            }
            persistence.complete(storeId, date, oldAnalysisId, source.analysisId(),
                    reservation, response.data(), now());
            return SolutionRegenerationOutcome.COMPLETED;
        } catch (RuntimeException exception) {
            log.warn("Solution regeneration failed for store {} date {}: {}",
                    storeId, date, exception.getClass().getSimpleName());
            persistence.fail(reservation, now());
            return SolutionRegenerationOutcome.FAILED;
        }
    }

    private Source source(Long storeId, LocalDate date) {
        var resolution = contexts.resolve(storeId, date);
        if (resolution.availability() != SalesSolutionGenerationContextResolver.Availability.READY) return null;
        var context = resolution.context();
        var patterns = context.metrics().conditionPatterns();
        if (patterns == null || patterns.isEmpty()) return null;
        Long analysisId = context.salesAnalysis().getId();
        var base = context.metrics();
        var forecast = weather.resolve(storeId, date).orElse(null);
        var metrics = new SalesSolutionMetrics(base.salesSummary(), base.hourlyProfile(),
                base.categoryBreakdown(), base.predictedSalesToday(), base.reviewSummary(),
                forecast, base.menuRankings(), base.businessHours(), base.conditionPatterns());
        return new Source(analysisId, metrics, fingerprints.of(analysisId, date, metrics));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(KOREA)); }
    private record Source(Long analysisId, SalesSolutionMetrics metrics, String fingerprint) {}
}
