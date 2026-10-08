package com.memme.repository.ranking;

import java.util.Optional;

import com.memme.entity.ranking.RankingProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingProfileRepository extends JpaRepository<RankingProfileEntity, Long> {

    Optional<RankingProfileEntity> findByStoreId(Long storeId);
}
