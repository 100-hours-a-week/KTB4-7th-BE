package com.memme.service.solution;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.store.Store;
import com.memme.repository.solution.SavedSolutionRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.repository.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolutionQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private SolutionBundleRepository bundleRepository;
    private SolutionRepository solutionRepository;
    private SavedSolutionRepository savedSolutionRepository;
    private StoreRepository storeRepository;
    private StoreOwnershipRepository ownershipRepository;
    private SalesSolutionGenerationContextResolver contextResolver;
    private SolutionQueryService service;

    @BeforeEach
    void setUp() {
        bundleRepository = mock(SolutionBundleRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        savedSolutionRepository = mock(SavedSolutionRepository.class);
        storeRepository = mock(StoreRepository.class);
        ownershipRepository = mock(StoreOwnershipRepository.class);
        contextResolver = mock(SalesSolutionGenerationContextResolver.class);
        service = new SolutionQueryService(
                bundleRepository,
                solutionRepository,
                savedSolutionRepository,
                storeRepository,
                ownershipRepository,
                contextResolver,
                Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void returnsAccuracyHelperForCompletedSolutionWithLessThanOneYearOfHistory() {
        givenAuthorizedStore();
        SolutionBundleEntity bundle = completedBundle();
        when(contextResolver.historyCoverage(10L, TODAY))
                .thenReturn(SalesSolutionGenerationContextResolver.HistoryCoverage.LIMITED);
        when(bundleRepository.findByStoreIdAndTargetDate(10L, TODAY)).thenReturn(Optional.of(bundle));
        when(solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(81L)).thenReturn(List.of());
        when(savedSolutionRepository.findAllByUserIdAndSolutionIdIn(1L, List.of())).thenReturn(List.of());

        var response = service.today(1L, 10L);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.data().helperText()).isEqualTo(
                "솔루션의 정확도를 높이려면 최소 1년 이상의 연속된 매출 데이터가 필요해요. "
                        + "데이터가 쌓일수록 더 정확한 분석을 제공할 수 있어요."
        );
    }

    @Test
    void omitsAccuracyHelperForCompletedSolutionWithAtLeastOneYearOfHistory() {
        givenAuthorizedStore();
        SolutionBundleEntity bundle = completedBundle();
        when(contextResolver.historyCoverage(10L, TODAY))
                .thenReturn(SalesSolutionGenerationContextResolver.HistoryCoverage.SUFFICIENT);
        when(bundleRepository.findByStoreIdAndTargetDate(10L, TODAY)).thenReturn(Optional.of(bundle));
        when(solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(81L)).thenReturn(List.of());
        when(savedSolutionRepository.findAllByUserIdAndSolutionIdIn(1L, List.of())).thenReturn(List.of());

        var response = service.today(1L, 10L);

        assertThat(response.data().helperText()).isNull();
    }

    @Test
    void returnsAccuracyHelperWhileLimitedHistorySolutionIsGenerating() {
        givenAuthorizedStore();
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(81L);
        when(bundle.getStatus()).thenReturn(SolutionBundleStatus.PENDING);
        when(bundle.getTargetDate()).thenReturn(TODAY);
        when(contextResolver.historyCoverage(10L, TODAY))
                .thenReturn(SalesSolutionGenerationContextResolver.HistoryCoverage.LIMITED);
        when(bundleRepository.findByStoreIdAndTargetDate(10L, TODAY)).thenReturn(Optional.of(bundle));

        var response = service.today(1L, 10L);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.data().helperText()).isEqualTo(
                "솔루션의 정확도를 높이려면 최소 1년 이상의 연속된 매출 데이터가 필요해요. "
                        + "데이터가 쌓일수록 더 정확한 분석을 제공할 수 있어요."
        );
    }

    @Test
    void returnsMinimumHistoryHelperWhenSolutionCannotBeGenerated() {
        givenAuthorizedStore();
        when(contextResolver.historyCoverage(10L, TODAY))
                .thenReturn(SalesSolutionGenerationContextResolver.HistoryCoverage.INSUFFICIENT);
        when(bundleRepository.findByStoreIdAndTargetDate(10L, TODAY)).thenReturn(Optional.empty());
        when(contextResolver.resolve(10L, TODAY)).thenReturn(
                new SalesSolutionGenerationContextResolver.Resolution(
                        SalesSolutionGenerationContextResolver.Availability.INSUFFICIENT_HISTORY,
                        null
                )
        );

        var response = service.today(1L, 10L);

        assertThat(response.status()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(response.data().helperText())
                .isEqualTo("솔루션 생성을 위해 최소 3개월 이상의 데이터가 필요합니다.");
    }

    private void givenAuthorizedStore() {
        Store store = mock(Store.class);
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(store.getStoreName()).thenReturn("맴매카페");
    }

    private SolutionBundleEntity completedBundle() {
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(81L);
        when(bundle.getStatus()).thenReturn(SolutionBundleStatus.COMPLETED);
        when(bundle.getTargetDate()).thenReturn(TODAY);
        return bundle;
    }
}
