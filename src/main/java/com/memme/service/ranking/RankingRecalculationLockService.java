package com.memme.service.ranking;

import com.memme.repository.ranking.RankingRecalculationLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingRecalculationLockService {

    private final RankingRecalculationLockRepository lockRepository;

    public RankingRecalculationLockService(RankingRecalculationLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(RankingPeriodCalculator.PeriodPair period) {
        lockRepository.insertIfAbsent(
                period.periodStart(),
                period.periodEnd(),
                period.comparisonStart(),
                period.comparisonEnd()
        );
        lockRepository.findByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEnd(
                        period.periodStart(),
                        period.periodEnd(),
                        period.comparisonStart(),
                        period.comparisonEnd()
                )
                .orElseThrow(() -> new IllegalStateException("랭킹 재계산 잠금 행을 찾을 수 없습니다."));
    }
}
