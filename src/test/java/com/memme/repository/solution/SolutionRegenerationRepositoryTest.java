package com.memme.repository.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.solution.SolutionRegenerationEntity;
import com.memme.entity.solution.SolutionRegenerationStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SolutionRegenerationRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final String FINGERPRINT = "b".repeat(64);

    @Autowired private SolutionRegenerationRepository regenerations;
    @Autowired private EntityManager entityManager;

    @Test
    void 매장_날짜_자료버전으로_완료_이력을_조회한다() {
        var saved = regenerations.saveAndFlush(SolutionRegenerationEntity.start(
                17L, DATE, FINGERPRINT, NOW));
        saved.complete(NOW.plusMinutes(1));
        regenerations.flush();
        entityManager.clear();

        assertThat(regenerations.findByStoreIdAndTargetDateAndSourceFingerprint(
                17L, DATE, FINGERPRINT)).get().satisfies(found -> {
            assertThat(found.getStatus()).isEqualTo(SolutionRegenerationStatus.COMPLETED);
            assertThat(found.getAttemptCount()).isEqualTo(1);
            assertThat(found.getCompletedAt()).isEqualTo(NOW.plusMinutes(1));
        });
    }

    @Test
    void 같은_날짜와_자료버전의_중복_이력을_거부한다() {
        regenerations.saveAndFlush(SolutionRegenerationEntity.start(
                17L, DATE, FINGERPRINT, NOW));
        entityManager.clear();

        assertThatThrownBy(() -> regenerations.saveAndFlush(SolutionRegenerationEntity.start(
                17L, DATE, FINGERPRINT, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 실패한_이력은_같은_행에서_재시도한다() {
        var saved = regenerations.saveAndFlush(SolutionRegenerationEntity.start(
                17L, DATE, FINGERPRINT, NOW));
        saved.fail(NOW.plusMinutes(1));
        saved.restart(NOW.plusMinutes(2));
        regenerations.flush();
        entityManager.clear();

        assertThat(regenerations.findById(saved.getId())).get().satisfies(found -> {
            assertThat(found.getStatus()).isEqualTo(SolutionRegenerationStatus.PROCESSING);
            assertThat(found.getAttemptCount()).isEqualTo(2);
            assertThat(found.getCompletedAt()).isNull();
        });
    }
}
