package com.memme.service.solution;

import java.time.Clock;
import java.time.LocalDate;

import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.exception.solution.SolutionManualRetryException;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;

import static com.memme.exception.solution.SolutionManualRetryException.Reason.DATA_NOT_READY;
import static com.memme.exception.solution.SolutionManualRetryException.Reason.NOT_ALLOWED;
import static com.memme.exception.solution.SolutionManualRetryException.Reason.STORE_OWNER_REQUIRED;

@Service
public class SolutionManualRetryService {

    // The current deployment has one BE instance. A database reservation is needed before scaling out.
    private final Object[] storeLocks = new Object[64];

    private final StoreOwnershipRepository ownershipRepository;
    private final SolutionBundleRepository bundleRepository;
    private final SalesSolutionGenerationContextResolver contextResolver;
    private final SalesSolutionGenerationService generationService;
    private final Clock clock;

    public SolutionManualRetryService(
            StoreOwnershipRepository ownershipRepository,
            SolutionBundleRepository bundleRepository,
            SalesSolutionGenerationContextResolver contextResolver,
            SalesSolutionGenerationService generationService,
            Clock clock
    ) {
        this.ownershipRepository = ownershipRepository;
        this.bundleRepository = bundleRepository;
        this.contextResolver = contextResolver;
        this.generationService = generationService;
        this.clock = clock;
        for (int i = 0; i < storeLocks.length; i++) {
            storeLocks[i] = new Object();
        }
    }

    public Result retry(Long userId, Long storeId) {
        if (userId == null || storeId == null
                || !ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SolutionManualRetryException(STORE_OWNER_REQUIRED);
        }
        synchronized (storeLocks[Math.floorMod(storeId.hashCode(), storeLocks.length)]) {
            LocalDate today = LocalDate.now(clock);
            boolean failedToday = bundleRepository.findByStoreIdAndTargetDate(storeId, today)
                    .filter(bundle -> bundle.getStatus() == SolutionBundleStatus.FAILED)
                    .isPresent();
            if (!failedToday) {
                throw new SolutionManualRetryException(NOT_ALLOWED);
            }
            if (contextResolver.resolve(storeId, today).availability()
                    != SalesSolutionGenerationContextResolver.Availability.READY) {
                throw new SolutionManualRetryException(DATA_NOT_READY);
            }

            SalesSolutionGenerationResult generated = generationService.generateScheduled(storeId, today);
            Status status = switch (generated.status()) {
                case COMPLETED -> Status.COMPLETED;
                case GENERATING -> Status.IN_PROGRESS;
                case FAILED -> Status.FAILED;
                case EMPTY, INSUFFICIENT_HISTORY, FORECAST_PENDING ->
                        throw new SolutionManualRetryException(DATA_NOT_READY);
            };
            return new Result(status, generated.solutionBundleId());
        }
    }

    public enum Status {
        COMPLETED,
        FAILED,
        IN_PROGRESS
    }

    public record Result(Status status, Long solutionBundleId) {}
}
