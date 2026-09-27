package com.memme.service.solution;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.memme.entity.solution.SavedSolutionEntity;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.exception.solution.SolutionRequestException;
import com.memme.repository.solution.SavedSolutionRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedSolutionServiceTest {

    private SavedSolutionRepository savedSolutionRepository;
    private SolutionBundleRepository bundleRepository;
    private SolutionRepository solutionRepository;
    private StoreOwnershipRepository ownershipRepository;
    private SavedSolutionService service;

    @BeforeEach
    void setUp() {
        bundleRepository = mock(SolutionBundleRepository.class);
        solutionRepository = mock(SolutionRepository.class);
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

    @Test
    void savesOnlyRequestedSolutionCard() {
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        SolutionEntity card = mock(SolutionEntity.class);
        when(card.getStoreId()).thenReturn(10L);
        when(card.getSolutionBundleId()).thenReturn(20L);
        when(solutionRepository.findById(30L)).thenReturn(Optional.of(card));
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getStatus()).thenReturn(SolutionBundleStatus.COMPLETED);
        when(bundle.getTargetDate()).thenReturn(java.time.LocalDate.of(2026, 9, 25));
        when(bundleRepository.findByIdAndStoreId(20L, 10L)).thenReturn(Optional.of(bundle));
        when(savedSolutionRepository.findByUserIdAndSolutionId(1L, 30L)).thenReturn(Optional.empty());
        SavedSolutionEntity saved = mock(SavedSolutionEntity.class);
        when(saved.getId()).thenReturn(91L);
        when(saved.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 24, 9, 0));
        when(savedSolutionRepository.save(org.mockito.ArgumentMatchers.any(SavedSolutionEntity.class)))
                .thenReturn(saved);

        SavedSolutionService.SaveResult result = service.save(1L, 10L, 30L);

        assertThat(result.created()).isTrue();
        assertThat(result.response().data().savedSolution().id()).isEqualTo(91L);
        verify(savedSolutionRepository).save(org.mockito.ArgumentMatchers.any(SavedSolutionEntity.class));
    }

    @Test
    void returnsExistingSaveWithoutCreatingDuplicate() {
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        SolutionEntity card = mock(SolutionEntity.class);
        when(card.getStoreId()).thenReturn(10L);
        when(card.getSolutionBundleId()).thenReturn(20L);
        when(solutionRepository.findById(30L)).thenReturn(Optional.of(card));
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getStatus()).thenReturn(SolutionBundleStatus.COMPLETED);
        when(bundle.getTargetDate()).thenReturn(java.time.LocalDate.of(2026, 9, 25));
        when(bundleRepository.findByIdAndStoreId(20L, 10L)).thenReturn(Optional.of(bundle));
        SavedSolutionEntity existing = mock(SavedSolutionEntity.class);
        when(existing.getId()).thenReturn(91L);
        when(existing.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 24, 9, 0));
        when(savedSolutionRepository.findByUserIdAndSolutionId(1L, 30L)).thenReturn(Optional.of(existing));

        SavedSolutionService.SaveResult result = service.save(1L, 10L, 30L);

        assertThat(result.created()).isFalse();
        assertThat(result.response().message()).isEqualTo("이미 저장된 솔루션입니다.");
    }

    @Test
    void deletesOnlyRequestedSavedCard() {
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        SavedSolutionEntity saved = mock(SavedSolutionEntity.class);
        when(saved.getUserId()).thenReturn(1L);
        when(saved.getSolutionId()).thenReturn(30L);
        when(savedSolutionRepository.findById(91L)).thenReturn(Optional.of(saved));
        SolutionEntity card = mock(SolutionEntity.class);
        when(card.getStoreId()).thenReturn(10L);
        when(solutionRepository.findById(30L)).thenReturn(Optional.of(card));

        int deletedCount = service.delete(1L, 10L, List.of(91L));

        assertThat(deletedCount).isEqualTo(1);
        verify(savedSolutionRepository).delete(saved);
    }

    @Test
    void listsEachSavedCardSeparatelyEvenWhenCardsBelongToSameBundle() {
        when(ownershipRepository.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
        SavedSolutionEntity firstSaved = mock(SavedSolutionEntity.class);
        when(firstSaved.getId()).thenReturn(92L);
        when(firstSaved.getSolutionId()).thenReturn(30L);
        when(firstSaved.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 24, 10, 0));
        SavedSolutionEntity secondSaved = mock(SavedSolutionEntity.class);
        when(secondSaved.getId()).thenReturn(91L);
        when(secondSaved.getSolutionId()).thenReturn(31L);
        when(secondSaved.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 24, 9, 0));
        when(savedSolutionRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(firstSaved, secondSaved));
        SolutionEntity firstCard = mock(SolutionEntity.class);
        when(firstCard.getId()).thenReturn(30L);
        when(firstCard.getTitle()).thenReturn("점심 할인");
        SolutionEntity secondCard = mock(SolutionEntity.class);
        when(secondCard.getId()).thenReturn(31L);
        when(secondCard.getTitle()).thenReturn("저녁 할인");
        when(solutionRepository.findAllById(List.of(30L, 31L))).thenReturn(List.of(firstCard, secondCard));

        var response = service.list(1L, 10L, null, 10);

        var items = response.data().groups().getFirst().items();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).savedId()).isEqualTo(92L);
        assertThat(items.get(0).remainingItemCount()).isZero();
        assertThat(items.get(1).savedId()).isEqualTo(91L);
        assertThat(items.get(1).remainingItemCount()).isZero();
    }
}
