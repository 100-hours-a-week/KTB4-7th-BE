package com.memme.repository.sales;

import com.memme.entity.sales.SeoulDistrictQuarterBenchmarkEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeoulDistrictQuarterBenchmarkRepository extends JpaRepository<SeoulDistrictQuarterBenchmarkEntity, Long> {

    Optional<SeoulDistrictQuarterBenchmarkEntity> findByImportedIdAndAdministrativeDongCode(
            Long importId, String administrativeDongCode);
}
