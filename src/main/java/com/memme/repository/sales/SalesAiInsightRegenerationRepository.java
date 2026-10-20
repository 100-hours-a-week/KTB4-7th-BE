package com.memme.repository.sales;

import com.memme.entity.sales.SalesAiInsightRegenerationEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesAiInsightRegenerationRepository
        extends JpaRepository<SalesAiInsightRegenerationEntity, Long> {

    Optional<SalesAiInsightRegenerationEntity> findByStoreIdAndTargetMonthAndSourceFingerprint(
            Long storeId, LocalDate targetMonth, String sourceFingerprint);
}
