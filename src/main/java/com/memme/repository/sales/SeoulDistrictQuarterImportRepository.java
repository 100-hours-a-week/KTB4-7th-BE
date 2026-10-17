package com.memme.repository.sales;

import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeoulDistrictQuarterImportRepository extends JpaRepository<SeoulDistrictQuarterImportEntity, Long> {

    Optional<SeoulDistrictQuarterImportEntity> findByBasisQuarterAndIndustryCode(
            String basisQuarter, String industryCode);

    List<SeoulDistrictQuarterImportEntity> findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
            String industryCode, SeoulDistrictQuarterImportStatus status, String latestQuarter);
}
