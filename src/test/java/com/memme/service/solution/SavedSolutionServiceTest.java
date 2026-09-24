package com.memme.service.solution;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.memme.entity.solution.SavedSolutionEntity;
import com.memme.exception.SolutionRequestException;
import com.memme.repository.solution.SavedSolutionRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SavedSolutionServiceTest {

    private SavedSolutionRepository savedSolutionRepository;
    private StoreOwnershipRepository ownershipRepository;
    private SavedSolutionService service;

    @BeforeEach
    void setUp() {
        SolutionBundleRepository bundleRepository = mock(SolutionBundleRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        savedSolutionRepository = mock(SavedSolutionRepository.class);
        ownershipRepository = mock(StoreOwnershipRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-24T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new SavedSolutionService(
                bundleRepository,
                solutionRepository,
                savedSolutionRepository,
                ownershipRepository,
                clock
        );
    }

    @Test
    void deletingMissingSavedIdIsIdempotent() {
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        when(savedSolutionRepository.findById(91L)).thenReturn(Optional.empty());

        int deletedCount = service.delete(1L, 10L, List.of(91L));

        assertThat(deletedCount).isZero();
    }

    @Test
    void rejectsDeletingAnotherUsersSavedSolution() {
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        SavedSolutionEntity saved = mock(SavedSolutionEntity.class);
        when(saved.getUserId()).thenReturn(2L);
        when(savedSolutionRepository.findById(91L)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.delete(1L, 10L, List.of(91L)))
                .isInstanceOfSatisfying(SolutionRequestException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(
                                SolutionRequestException.Reason.SAVED_SOLUTION_OWNER_REQUIRED
                        ));
    }
}
