package com.memme.service.solution;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.exception.solution.SolutionRequestException;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolutionManualRetryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private final StoreOwnershipRepository ownershipRepository = mock(StoreOwnershipRepository.class);
    private final SolutionBundleRepository bundleRepository = mock(SolutionBundleRepository.class);
    private final SalesSolutionGenerationContextResolver contextResolver = mock(SalesSolutionGenerationContextResolver.class);
    private final SalesSolutionGenerationService generationService = mock(SalesSolutionGenerationService.class);
    private SolutionManualRetryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new SolutionManualRetryService(
                ownershipRepository, bundleRepository, contextResolver, generationService, clock);
        when(ownershipRepository.existsActiveStoreOwnedBy(7L, 3L)).thenReturn(true);
    }

    @Test
    void rejectsAnotherStoreOwnerBeforeLookingUpBundle() {
        assertThatThrownBy(() -> service.retry(99L, 7L))
                .isInstanceOf(SolutionRequestException.class)
                .satisfies(error -> assertThat(((SolutionRequestException) error).getReason())
                        .isEqualTo(SolutionRequestException.Reason.STORE_OWNER_REQUIRED));
        verify(bundleRepository, never()).findByStoreIdAndTargetDate(any(), any());
    }

    @Test
    void doesNotGenerateWhenTodayHasNoFailedBundle() {
        SolutionBundleEntity bundle = bundle(SolutionBundleStatus.COMPLETED);
        when(bundleRepository.findByStoreIdAndTargetDate(7L, TODAY))
                .thenReturn(Optional.of(bundle));

        assertThat(service.retry(3L, 7L).status())
                .isEqualTo(SolutionManualRetryService.Status.NOT_ALLOWED);
        verify(contextResolver, never()).resolve(any(), any());
        verify(generationService, never()).generateScheduled(any(), any());
    }

    @Test
    void doesNotGenerateWhenRequiredForecastIsMissing() {
        SolutionBundleEntity bundle = bundle(SolutionBundleStatus.FAILED);
        when(bundleRepository.findByStoreIdAndTargetDate(7L, TODAY))
                .thenReturn(Optional.of(bundle));
        when(contextResolver.resolve(7L, TODAY)).thenReturn(new SalesSolutionGenerationContextResolver.Resolution(
                SalesSolutionGenerationContextResolver.Availability.FORECAST_PENDING, null));

        assertThat(service.retry(3L, 7L).status())
                .isEqualTo(SolutionManualRetryService.Status.DATA_NOT_READY);
        verify(generationService, never()).generateScheduled(any(), any());
    }

    @Test
    void regeneratesOnlyTodaysFailedBundleWhenAllDataIsReady() {
        SolutionBundleEntity bundle = bundle(SolutionBundleStatus.FAILED);
        when(bundleRepository.findByStoreIdAndTargetDate(7L, TODAY))
                .thenReturn(Optional.of(bundle));
        when(contextResolver.resolve(7L, TODAY)).thenReturn(new SalesSolutionGenerationContextResolver.Resolution(
                SalesSolutionGenerationContextResolver.Availability.READY, mock(SalesSolutionGenerationContext.class)));
        when(generationService.generateScheduled(7L, TODAY))
                .thenReturn(SalesSolutionGenerationResult.of(SalesSolutionGenerationResult.Status.COMPLETED, 31L));

        SolutionManualRetryService.Result result = service.retry(3L, 7L);

        assertThat(result.status()).isEqualTo(SolutionManualRetryService.Status.COMPLETED);
        assertThat(result.solutionBundleId()).isEqualTo(31L);
    }

    @Test
    void returnsFailedWhenAiGenerationFailsAgain() {
        SolutionBundleEntity bundle = bundle(SolutionBundleStatus.FAILED);
        when(bundleRepository.findByStoreIdAndTargetDate(7L, TODAY))
                .thenReturn(Optional.of(bundle));
        when(contextResolver.resolve(7L, TODAY)).thenReturn(new SalesSolutionGenerationContextResolver.Resolution(
                SalesSolutionGenerationContextResolver.Availability.READY,
                mock(SalesSolutionGenerationContext.class)));
        when(generationService.generateScheduled(7L, TODAY))
                .thenReturn(SalesSolutionGenerationResult.of(SalesSolutionGenerationResult.Status.FAILED, 31L));

        SolutionManualRetryService.Result result = service.retry(3L, 7L);

        assertThat(result.status()).isEqualTo(SolutionManualRetryService.Status.FAILED);
        assertThat(result.solutionBundleId()).isEqualTo(31L);
    }

    @Test
    void serializesConcurrentRetriesForTheSameStore() throws Exception {
        AtomicReference<SolutionBundleStatus> bundleStatus =
                new AtomicReference<>(SolutionBundleStatus.FAILED);
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getStatus()).thenAnswer(ignored -> bundleStatus.get());
        when(bundleRepository.findByStoreIdAndTargetDate(7L, TODAY))
                .thenReturn(Optional.of(bundle));
        when(contextResolver.resolve(7L, TODAY)).thenReturn(new SalesSolutionGenerationContextResolver.Resolution(
                SalesSolutionGenerationContextResolver.Availability.READY,
                mock(SalesSolutionGenerationContext.class)));

        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch allowGenerationToFinish = new CountDownLatch(1);
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        AtomicInteger generationCalls = new AtomicInteger();
        when(generationService.generateScheduled(7L, TODAY)).thenAnswer(ignored -> {
            generationCalls.incrementAndGet();
            generationStarted.countDown();
            if (!allowGenerationToFinish.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("generation test timed out");
            }
            bundleStatus.set(SolutionBundleStatus.COMPLETED);
            return SalesSolutionGenerationResult.of(SalesSolutionGenerationResult.Status.COMPLETED, 31L);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SolutionManualRetryService.Result> first =
                    executor.submit(() -> service.retry(3L, 7L));
            assertThat(generationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<SolutionManualRetryService.Result> second =
                    executor.submit(() -> {
                        secondThread.set(Thread.currentThread());
                        secondRequestStarted.countDown();
                        return service.retry(3L, 7L);
                    });
            assertThat(secondRequestStarted.await(1, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (System.nanoTime() < deadline
                    && secondThread.get().getState() != Thread.State.BLOCKED
                    && generationCalls.get() == 1) {
                Thread.yield();
            }
            assertThat(secondThread.get().getState()).isEqualTo(Thread.State.BLOCKED);

            allowGenerationToFinish.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(SolutionManualRetryService.Status.COMPLETED);
            assertThat(second.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(SolutionManualRetryService.Status.NOT_ALLOWED);
            assertThat(generationCalls).hasValue(1);
        } finally {
            allowGenerationToFinish.countDown();
            executor.shutdownNow();
        }
    }

    private SolutionBundleEntity bundle(SolutionBundleStatus status) {
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getStatus()).thenReturn(status);
        return bundle;
    }
}
