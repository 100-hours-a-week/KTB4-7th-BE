package com.memme.service.solution;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.memme.dto.solution.SavedSolutionDetailResponse;
import com.memme.dto.solution.SavedSolutionListResponse;
import com.memme.dto.solution.SolutionSaveResponse;
import com.memme.entity.solution.SavedSolutionEntity;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.exception.solution.SolutionRequestException;
import com.memme.repository.solution.SavedSolutionRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.solution.SolutionRequestException.Reason.BUNDLE_NOT_FOUND;
import static com.memme.exception.solution.SolutionRequestException.Reason.INVALID_PAGE_SIZE;
import static com.memme.exception.solution.SolutionRequestException.Reason.INVALID_SAVED_IDS;
import static com.memme.exception.solution.SolutionRequestException.Reason.SAVED_SOLUTION_NOT_FOUND;
import static com.memme.exception.solution.SolutionRequestException.Reason.SAVED_SOLUTION_OWNER_REQUIRED;
import static com.memme.exception.solution.SolutionRequestException.Reason.SAVE_EXPIRED;
import static com.memme.exception.solution.SolutionRequestException.Reason.SAVE_TARGET_NOT_FOUND;
import static com.memme.exception.solution.SolutionRequestException.Reason.STORE_OWNER_REQUIRED;

@Service
public class SavedSolutionService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MM/dd");

    private final SolutionBundleRepository bundleRepository;
    private final SolutionRepository solutionRepository;
    private final SavedSolutionRepository savedSolutionRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final Clock clock;

    public SavedSolutionService(
            SolutionBundleRepository bundleRepository,
            SolutionRepository solutionRepository,
            SavedSolutionRepository savedSolutionRepository,
            StoreOwnershipRepository ownershipRepository,
            Clock clock
    ) {
        this.bundleRepository = bundleRepository;
        this.solutionRepository = solutionRepository;
        this.savedSolutionRepository = savedSolutionRepository;
        this.ownershipRepository = ownershipRepository;
        this.clock = clock;
    }

    @Transactional
    public SaveResult saveBundle(Long userId, Long storeId, Long bundleId) {
        requireOwnership(userId, storeId);
        SolutionBundleEntity bundle = bundleRepository.findByIdAndStoreId(bundleId, storeId)
                .orElseThrow(() -> new SolutionRequestException(SAVE_TARGET_NOT_FOUND));
        if (bundle.getStatus() != SolutionBundleStatus.COMPLETED) {
            throw new SolutionRequestException(SAVE_TARGET_NOT_FOUND);
        }
        if (bundle.getTargetDate().isBefore(LocalDate.now(clock))) {
            throw new SolutionRequestException(SAVE_EXPIRED);
        }
        List<SolutionEntity> cards = solutionRepository
                .findAllBySolutionBundleIdOrderByRankNoAsc(bundleId);
        List<SavedSolutionEntity> existing = savedSolutionRepository
                .findAllByUserIdAndSolutionIdIn(userId, cardIds(cards));
        boolean created = existing.size() != cards.size();
        Map<Long, SavedSolutionEntity> bySolutionId = new LinkedHashMap<>();
        existing.forEach(saved -> bySolutionId.put(saved.getSolutionId(), saved));
        for (SolutionEntity card : cards) {
            bySolutionId.computeIfAbsent(card.getId(), solutionId ->
                    savedSolutionRepository.save(SavedSolutionEntity.create(userId, solutionId)));
        }
        SavedSolutionEntity representative = bySolutionId.values().stream()
                .min(Comparator.comparing(SavedSolutionEntity::getId))
                .orElseThrow(() -> new IllegalStateException("completed bundle has no cards"));
        SolutionSaveResponse response = new SolutionSaveResponse(
                created ? "솔루션이 저장되었습니다." : "이미 저장된 솔루션입니다.",
                new SolutionSaveResponse.Data(
                        new SolutionSaveResponse.SavedSolution(
                                representative.getId(),
                                bundleId,
                                bundle.getTargetDate(),
                                representative.getCreatedAt()
                        ),
                        "SOL-03"
                )
        );
        return new SaveResult(response, created);
    }

    @Transactional(readOnly = true)
    public SavedSolutionListResponse list(Long userId, Long storeId, Long cursor, int size) {
        requireOwnership(userId, storeId);
        if (size < 1 || size > 20) {
            throw new SolutionRequestException(INVALID_PAGE_SIZE);
        }
        List<SavedBundle> bundles = savedBundles(userId).stream()
                .filter(bundle -> cursor == null || bundle.representative().getId() < cursor)
                .limit(size + 1L)
                .toList();
        boolean hasNext = bundles.size() > size;
        List<SavedBundle> page = hasNext ? bundles.subList(0, size) : bundles;
        Map<Integer, List<SavedSolutionListResponse.Item>> groups = new LinkedHashMap<>();
        for (SavedBundle bundle : page) {
            LocalDate savedDate = bundle.representative().getCreatedAt().toLocalDate();
            SolutionEntity first = bundle.cards().getFirst();
            int remaining = bundle.cards().size() - 1;
            String displayTitle = DISPLAY_DATE.format(savedDate) + " " + first.getTitle()
                    + (remaining > 0 ? " 외 " + remaining + "개" : "");
            groups.computeIfAbsent(savedDate.getYear(), ignored -> new ArrayList<>())
                    .add(new SavedSolutionListResponse.Item(
                            bundle.representative().getId(),
                            savedDate,
                            displayTitle,
                            first.getTitle(),
                            remaining
                    ));
        }
        List<SavedSolutionListResponse.YearGroup> yearGroups = groups.entrySet().stream()
                .map(entry -> new SavedSolutionListResponse.YearGroup(
                        entry.getKey(),
                        List.copyOf(entry.getValue())
                ))
                .toList();
        Long nextCursor = hasNext && !page.isEmpty()
                ? page.getLast().representative().getId()
                : null;
        return new SavedSolutionListResponse(
                page.isEmpty() ? "아직 저장된 솔루션이 없습니다." : "조회에 성공했습니다.",
                nextCursor,
                new SavedSolutionListResponse.Data(yearGroups)
        );
    }

    @Transactional(readOnly = true)
    public SavedSolutionDetailResponse detail(Long userId, Long storeId, Long savedId) {
        requireOwnership(userId, storeId);
        SavedSolutionEntity representative = savedSolutionRepository.findByIdAndUserId(savedId, userId)
                .orElseThrow(() -> new SolutionRequestException(SAVED_SOLUTION_NOT_FOUND));
        SolutionEntity selected = solutionRepository.findById(representative.getSolutionId())
                .filter(solution -> storeId.equals(solution.getStoreId()))
                .orElseThrow(() -> new SolutionRequestException(SAVED_SOLUTION_NOT_FOUND));
        SolutionBundleEntity bundle = requireBundle(storeId, selected.getSolutionBundleId());
        List<SolutionEntity> cards = solutionRepository
                .findAllBySolutionBundleIdOrderByRankNoAsc(bundle.getId());
        List<Long> savedSolutionIds = savedSolutionRepository
                .findAllByUserIdAndSolutionIdIn(userId, cardIds(cards)).stream()
                .map(SavedSolutionEntity::getSolutionId)
                .toList();
        List<SavedSolutionDetailResponse.Item> savedCards = cards.stream()
                .filter(card -> savedSolutionIds.contains(card.getId()))
                .map(card -> new SavedSolutionDetailResponse.Item(
                        card.getRankNo(),
                        card.getTitle(),
                        card.getSummaryText(),
                        card.getDetailText(),
                        card.getEvidenceText()
                ))
                .toList();
        LocalDate savedDate = representative.getCreatedAt().toLocalDate();
        SolutionEntity first = savedCards.isEmpty() ? selected : cards.getFirst();
        int remaining = Math.max(savedCards.size() - 1, 0);
        String displayTitle = DISPLAY_DATE.format(savedDate) + " " + first.getTitle()
                + (remaining > 0 ? " 외 " + remaining + "개" : "");
        return new SavedSolutionDetailResponse(
                "조회에 성공했습니다.",
                new SavedSolutionDetailResponse.Data(
                        new SavedSolutionDetailResponse.SavedSolution(
                                representative.getId(),
                                savedDate,
                                displayTitle,
                                true,
                                savedCards
                        )
                )
        );
    }

    @Transactional
    public int delete(Long userId, Long storeId, List<Long> savedIds) {
        requireOwnership(userId, storeId);
        if (savedIds == null || savedIds.isEmpty()) {
            throw new SolutionRequestException(INVALID_SAVED_IDS);
        }
        int deletedCount = 0;
        for (Long savedId : savedIds.stream().distinct().toList()) {
            SavedSolutionEntity saved = savedSolutionRepository.findById(savedId).orElse(null);
            if (saved == null) {
                continue;
            }
            if (!userId.equals(saved.getUserId())) {
                throw new SolutionRequestException(SAVED_SOLUTION_OWNER_REQUIRED);
            }
            SolutionEntity solution = solutionRepository.findById(saved.getSolutionId()).orElse(null);
            if (solution == null) {
                continue;
            }
            if (!storeId.equals(solution.getStoreId())) {
                throw new SolutionRequestException(SAVED_SOLUTION_OWNER_REQUIRED);
            }
            List<SolutionEntity> cards = solutionRepository
                    .findAllBySolutionBundleIdOrderByRankNoAsc(solution.getSolutionBundleId());
            List<SavedSolutionEntity> rows = savedSolutionRepository
                    .findAllByUserIdAndSolutionIdIn(userId, cardIds(cards));
            savedSolutionRepository.deleteAll(rows);
            deletedCount += rows.size();
        }
        return deletedCount;
    }

    private List<SavedBundle> savedBundles(Long userId) {
        List<SavedSolutionEntity> saved = savedSolutionRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
        if (saved.isEmpty()) {
            return List.of();
        }
        Map<Long, SolutionEntity> solutionById = new LinkedHashMap<>();
        solutionRepository.findAllById(saved.stream().map(SavedSolutionEntity::getSolutionId).toList())
                .forEach(solution -> solutionById.put(solution.getId(), solution));
        List<Long> bundleIds = solutionById.values().stream()
                .map(SolutionEntity::getSolutionBundleId)
                .distinct()
                .toList();
        Map<Long, List<SavedSolutionEntity>> savedByBundle = new LinkedHashMap<>();
        Map<Long, List<SolutionEntity>> cardsByBundle = new LinkedHashMap<>();
        solutionRepository
                .findAllBySolutionBundleIdInOrderBySolutionBundleIdAscRankNoAsc(bundleIds)
                .forEach(solution -> cardsByBundle
                        .computeIfAbsent(solution.getSolutionBundleId(), ignored -> new ArrayList<>())
                        .add(solution));
        for (SavedSolutionEntity row : saved) {
            SolutionEntity solution = solutionById.get(row.getSolutionId());
            if (solution != null) {
                savedByBundle.computeIfAbsent(solution.getSolutionBundleId(), ignored -> new ArrayList<>())
                        .add(row);
            }
        }
        return savedByBundle.entrySet().stream()
                .map(entry -> new SavedBundle(
                        entry.getValue().stream()
                                .min(Comparator.comparing(SavedSolutionEntity::getId))
                                .orElseThrow(),
                        cardsByBundle.get(entry.getKey()),
                        entry.getValue().stream()
                                .map(SavedSolutionEntity::getCreatedAt)
                                .max(LocalDateTime::compareTo)
                                .orElseThrow()
                ))
                .sorted(Comparator.comparing(SavedBundle::savedAt).reversed()
                        .thenComparing(bundle -> bundle.representative().getId(), Comparator.reverseOrder()))
                .toList();
    }

    private Collection<Long> cardIds(List<SolutionEntity> cards) {
        return cards.stream().map(SolutionEntity::getId).toList();
    }

    private SolutionBundleEntity requireBundle(Long storeId, Long bundleId) {
        return bundleRepository.findByIdAndStoreId(bundleId, storeId)
                .orElseThrow(() -> new SolutionRequestException(BUNDLE_NOT_FOUND));
    }

    private void requireOwnership(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SolutionRequestException(STORE_OWNER_REQUIRED);
        }
    }

    public record SaveResult(SolutionSaveResponse response, boolean created) {}

    private record SavedBundle(
            SavedSolutionEntity representative,
            List<SolutionEntity> cards,
            LocalDateTime savedAt
    ) {}
}
