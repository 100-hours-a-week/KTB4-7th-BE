package com.memme.repository.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionEntity;
import com.memme.entity.solution.SolutionType;
import com.memme.entity.solution.SavedSolutionEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SolutionArchiveRepositoryTest {

    @Autowired private SolutionBundleRepository bundles;
    @Autowired private SolutionRepository cards;
    @Autowired private SavedSolutionRepository savedCards;
    @Autowired private EntityManager entityManager;

    @Test
    void archivedCardKeepsDisplayRankAndIdButDisappearsFromCurrentQueries() {
        var bundle = bundles.saveAndFlush(SolutionBundleEntity.create(
                90L, 91L, LocalDate.of(2026, 10, 20)));
        var oldCard = cards.saveAndFlush(card(bundle.getId(), 1, "original"));
        Long oldId = oldCard.getId();
        var saved = savedCards.saveAndFlush(SavedSolutionEntity.create(700L, oldId));

        oldCard.archive(2);
        cards.flush();
        cards.saveAndFlush(card(bundle.getId(), 1, "replacement"));
        entityManager.clear();

        assertThat(cards.findAllBySolutionBundleIdOrderByRankNoAsc(bundle.getId()))
                .extracting(SolutionEntity::getTitle).containsExactly("replacement");
        assertThat(cards.findAllBySolutionBundleIdInOrderBySolutionBundleIdAscRankNoAsc(
                java.util.List.of(bundle.getId())))
                .extracting(SolutionEntity::getTitle).containsExactly("replacement");
        assertThat(cards.findAllIncludingArchivedBySolutionBundleIdOrderByRankNoAsc(bundle.getId()))
                .hasSize(2);
        assertThat(cards.findById(oldId)).get().satisfies(archived -> {
            assertThat(archived.getRankNo()).isEqualTo(1);
            assertThat(archived.getStorageRankNo()).isEqualTo(2);
            assertThat(archived.getOriginalRankNo()).isEqualTo(1);
            assertThat(archived.isActive()).isFalse();
        });
        assertThat(cards.findMaxRankNoBySolutionBundleId(bundle.getId())).isEqualTo(2);
        assertThat(savedCards.findById(saved.getId())).get()
                .extracting(SavedSolutionEntity::getSolutionId).isEqualTo(oldId);
    }

    @Test
    void archiveRejectsNonPositiveRankAndSecondArchive() {
        var card = card(1L, 1, "original");
        assertThatThrownBy(() -> card.archive(0)).isInstanceOf(IllegalArgumentException.class);
        card.archive(2);
        assertThatThrownBy(() -> card.archive(3)).isInstanceOf(IllegalStateException.class);
    }

    private SolutionEntity card(Long bundleId, int rank, String title) {
        return SolutionEntity.create(90L, 91L, bundleId, SolutionType.DAILY,
                title, "summary", "detail", null, LocalDateTime.of(2026, 10, 20, 12, 0), rank);
    }
}
