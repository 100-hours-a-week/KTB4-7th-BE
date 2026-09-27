package com.memme.service.solution;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    public SaveResult save(Long userId, Long storeId, Long solutionId) {
        requireOwnership(userId, storeId);
        SolutionEntity card = solutionRepository.findById(solutionId)
                .filter(solution -> storeId.equals(solution.getStoreId()))
                .orElseThrow(() -> new SolutionRequestException(SAVE_TARGET_NOT_FOUND));
        SolutionBundleEntity bundle = requireBundle(storeId, card.getSolutionBundleId());
        if (bundle.getStatus() != SolutionBundleStatus.COMPLETED) {
            throw new SolutionRequestException(SAVE_TARGET_NOT_FOUND);
        }
        if (bundle.getTargetDate().isBefore(LocalDate.now(clock))) {
            throw new SolutionRequestException(SAVE_EXPIRED);
        }
        var existing = savedSolutionRepository.findByUserIdAndSolutionId(userId, solutionId);
        boolean created = existing.isEmpty();
        SavedSolutionEntity saved = existing.orElseGet(
                () -> savedSolutionRepository.save(SavedSolutionEntity.create(userId, solutionId))
        );
        return new SaveResult(
                new SolutionSaveResponse(
                        created ? "솔루션이 저장되었습니다." : "이미 저장된 솔루션입니다.",
                        new SolutionSaveResponse.Data(
                                new SolutionSaveResponse.SavedSolution(
                                        saved.getId(),
                                        bundle.getId(),
                                        bundle.getTargetDate(),
                                        saved.getCreatedAt()
                                ),
                                "SOL-03"
                        )
                ),
                created
        );
    }

    @Transactional(readOnly = true)
    public SavedSolutionListResponse list(Long userId, Long storeId, Long cursor, int size) {
        requireOwnership(userId, storeId);
        if (size < 1 || size > 20) {
            throw new SolutionRequestException(INVALID_PAGE_SIZE);
        }
        List<SavedSolutionEntity> saved = savedSolutionRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(row -> cursor == null || row.getId() < cursor)
                .limit(size + 1L)
                .toList();
        boolean hasNext = saved.size() > size;
        List<SavedSolutionEntity> page = hasNext ? saved.subList(0, size) : saved;
        Map<Long, SolutionEntity> cardsById = new LinkedHashMap<>();
        solutionRepository.findAllById(page.stream().map(SavedSolutionEntity::getSolutionId).toList())
                .forEach(card -> cardsById.put(card.getId(), card));
        Map<Integer, List<SavedSolutionListResponse.Item>> groups = new LinkedHashMap<>();
        for (SavedSolutionEntity row : page) {
            SolutionEntity card = cardsById.get(row.getSolutionId());
            if (card == null) {
                continue;
            }
            LocalDate savedDate = row.getCreatedAt().toLocalDate();
            String displayTitle = DISPLAY_DATE.format(savedDate) + " " + card.getTitle();
            groups.computeIfAbsent(savedDate.getYear(), ignored -> new ArrayList<>())
                    .add(new SavedSolutionListResponse.Item(
                            row.getId(),
                            savedDate,
                            displayTitle,
                            card.getTitle(),
                            0
                    ));
        }
        List<SavedSolutionListResponse.YearGroup> yearGroups = groups.entrySet().stream()
                .map(entry -> new SavedSolutionListResponse.YearGroup(
                        entry.getKey(),
                        List.copyOf(entry.getValue())
                ))
                .toList();
        Long nextCursor = hasNext && !page.isEmpty() ? page.getLast().getId() : null;
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
        LocalDate savedDate = representative.getCreatedAt().toLocalDate();
        String displayTitle = DISPLAY_DATE.format(savedDate) + " " + selected.getTitle();
        return new SavedSolutionDetailResponse(
                "조회에 성공했습니다.",
                new SavedSolutionDetailResponse.Data(
                        new SavedSolutionDetailResponse.SavedSolution(
                                representative.getId(),
                                savedDate,
                                displayTitle,
                                true,
                                List.of(new SavedSolutionDetailResponse.Item(
                                        selected.getRankNo(),
                                        selected.getTitle(),
                                        selected.getSummaryText(),
                                        selected.getDetailText(),
                                        selected.getEvidenceText()
                                ))
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
            savedSolutionRepository.delete(saved);
            deletedCount++;
        }
        return deletedCount;
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
}
