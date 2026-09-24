package com.memme.service.solution;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.memme.dto.solution.SolutionBundleDetailResponse;
import com.memme.dto.solution.SolutionCardResponse;
import com.memme.dto.solution.SolutionTodayResponse;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.exception.SolutionRequestException;
import com.memme.repository.solution.SavedSolutionRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.repository.store.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.SolutionRequestException.Reason.BUNDLE_EXPIRED;
import static com.memme.exception.SolutionRequestException.Reason.BUNDLE_NOT_FOUND;
import static com.memme.exception.SolutionRequestException.Reason.STORE_NOT_FOUND;
import static com.memme.exception.SolutionRequestException.Reason.STORE_OWNER_REQUIRED;

@Service
@Transactional(readOnly = true)
public class SolutionQueryService {

    private final SolutionBundleRepository bundleRepository;
    private final SolutionRepository solutionRepository;
    private final SavedSolutionRepository savedSolutionRepository;
    private final StoreRepository storeRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final SalesSolutionGenerationContextResolver contextResolver;
    private final Clock clock;

    public SolutionQueryService(
            SolutionBundleRepository bundleRepository,
            SolutionRepository solutionRepository,
            SavedSolutionRepository savedSolutionRepository,
            StoreRepository storeRepository,
            StoreOwnershipRepository ownershipRepository,
            SalesSolutionGenerationContextResolver contextResolver,
            Clock clock
    ) {
        this.bundleRepository = bundleRepository;
        this.solutionRepository = solutionRepository;
        this.savedSolutionRepository = savedSolutionRepository;
        this.storeRepository = storeRepository;
        this.ownershipRepository = ownershipRepository;
        this.contextResolver = contextResolver;
        this.clock = clock;
    }

    public SolutionTodayResponse today(Long userId, Long storeId) {
        requireOwnership(userId, storeId);
        String storeName = storeRepository.findById(storeId)
                .orElseThrow(() -> new SolutionRequestException(STORE_NOT_FOUND))
                .getStoreName();
        LocalDate today = LocalDate.now(clock);
        return bundleRepository.findByStoreIdAndTargetDate(storeId, today)
                .map(bundle -> todayFromBundle(storeName, bundle))
                .orElseGet(() -> todayWithoutBundle(storeId, today));
    }

    public SolutionBundleDetailResponse detail(Long userId, Long storeId, Long bundleId) {
        requireOwnership(userId, storeId);
        SolutionBundleEntity bundle = bundleRepository.findByIdAndStoreId(bundleId, storeId)
                .orElseThrow(() -> new SolutionRequestException(BUNDLE_NOT_FOUND));
        List<SolutionEntity> cards = solutionRepository
                .findAllBySolutionBundleIdOrderByRankNoAsc(bundleId);
        boolean saved = !savedSolutionRepository
                .findAllByUserIdAndSolutionIdIn(userId, cardIds(cards))
                .isEmpty();
        if (bundle.getTargetDate().isBefore(LocalDate.now(clock)) && !saved) {
            throw new SolutionRequestException(BUNDLE_EXPIRED);
        }
        return new SolutionBundleDetailResponse(
                "조회에 성공했습니다.",
                new SolutionBundleDetailResponse.Data(new SolutionBundleDetailResponse.SolutionBundle(
                        bundle.getId(),
                        bundle.getTargetDate(),
                        bundle.getTargetDate().plusDays(1).atStartOfDay(clock.getZone())
                                .toOffsetDateTime(),
                        "오늘의 솔루션은 00:00시에 사라져요. 남겨두려면 저장해주세요.",
                        saved,
                        cards.stream().map(this::toCard).toList()
                ))
        );
    }

    private SolutionTodayResponse todayFromBundle(String storeName, SolutionBundleEntity bundle) {
        List<SolutionCardResponse> cards = bundle.getStatus() == SolutionBundleStatus.COMPLETED
                ? solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(bundle.getId())
                        .stream().map(this::toCard).toList()
                : List.of();
        String status = bundle.getStatus().name();
        String message = switch (bundle.getStatus()) {
            case PENDING, GENERATING -> "오늘의 솔루션을 생성하고 있습니다.";
            case COMPLETED -> "조회에 성공했습니다.";
            case FAILED -> "오늘의 솔루션을 불러오지 못했습니다. 다시 시도해주세요.";
        };
        boolean completed = bundle.getStatus() == SolutionBundleStatus.COMPLETED;
        return response(
                message,
                status,
                completed ? storeName : null,
                bundle.getStatus() == SolutionBundleStatus.FAILED ? null : bundle.getId(),
                bundle.getTargetDate(),
                cards
        );
    }

    private SolutionTodayResponse todayWithoutBundle(
            Long storeId,
            LocalDate targetDate
    ) {
        SalesSolutionGenerationContextResolver.Resolution resolution =
                contextResolver.resolve(storeId, targetDate);
        return switch (resolution.availability()) {
            case EMPTY -> response(
                    "데이터를 추가해 매출 분석을 받아보세요.",
                    "EMPTY",
                    null,
                    null,
                    targetDate,
                    List.of()
            );
            case INSUFFICIENT_HISTORY -> response(
                    "솔루션 생성을 위한 매출 이력이 부족합니다.",
                    "INSUFFICIENT_HISTORY",
                    null,
                    null,
                    targetDate,
                    List.of()
            );
            case FORECAST_PENDING -> response(
                    "매출 예측을 생성하고 있습니다.",
                    "GENERATING",
                    null,
                    null,
                    targetDate,
                    List.of()
            );
            case READY -> response(
                    "오늘의 솔루션을 생성하고 있습니다.",
                    "GENERATING",
                    null,
                    null,
                    targetDate,
                    List.of()
            );
        };
    }

    private SolutionTodayResponse response(
            String message,
            String status,
            String storeName,
            Long bundleId,
            LocalDate targetDate,
            List<SolutionCardResponse> cards
    ) {
        return new SolutionTodayResponse(
                message,
                status,
                new SolutionTodayResponse.Data(
                        storeName,
                        storeName == null ? null : storeName + " 맴매 솔루션",
                        bundleId,
                        targetDate,
                        cards
                )
        );
    }

    private SolutionCardResponse toCard(SolutionEntity card) {
        return new SolutionCardResponse(
                card.getId(),
                card.getRankNo(),
                card.getTitle(),
                card.getSummaryText(),
                card.getDetailText(),
                card.getEvidenceText()
        );
    }

    private Collection<Long> cardIds(List<SolutionEntity> cards) {
        return cards.stream().map(SolutionEntity::getId).toList();
    }

    private void requireOwnership(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SolutionRequestException(STORE_OWNER_REQUIRED);
        }
    }
}
