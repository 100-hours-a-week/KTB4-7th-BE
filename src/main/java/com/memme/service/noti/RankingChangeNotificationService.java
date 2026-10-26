package com.memme.service.noti;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.ranking.RankingEntryEntity;
import com.memme.entity.ranking.RankingProfileEntity;
import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.ranking.RankingPeriodCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingChangeNotificationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String RELATED_ENTITY_TYPE = "RANKING_SNAPSHOT";

    private final RankingSnapshotRepository snapshotRepository;
    private final RankingEntryRepository entryRepository;
    private final RankingProfileRepository profileRepository;
    private final StoreRepository storeRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ZoneId snapshotTimestampZone;
    private final NotificationSsePublicationService publisher;

    @Autowired
    public RankingChangeNotificationService(
            RankingSnapshotRepository snapshotRepository,
            RankingEntryRepository entryRepository,
            RankingProfileRepository profileRepository,
            StoreRepository storeRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationSsePublicationService publisher
    ) {
        this(
                snapshotRepository,
                entryRepository,
                profileRepository,
                storeRepository,
                preferenceRepository,
                notificationRepository,
                userRepository,
                clock,
                ZoneId.systemDefault(),
                publisher
        );
    }

    RankingChangeNotificationService(
            RankingSnapshotRepository snapshotRepository,
            RankingEntryRepository entryRepository,
            RankingProfileRepository profileRepository,
            StoreRepository storeRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock,
            ZoneId snapshotTimestampZone,
            NotificationSsePublicationService publisher
    ) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
        this.profileRepository = profileRepository;
        this.storeRepository = storeRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.snapshotTimestampZone = snapshotTimestampZone;
        this.publisher = publisher;
    }

    @Transactional
    public void createDailyRankingChangeNotifications(LocalDate baseDate) {
        LocalDate previousDate = baseDate.minusDays(1);
        RankingPeriodCalculator.PeriodPair period = RankingPeriodCalculator.current(clock);

        Optional<RankingSnapshotEntity> currentSnapshot = findLatestCompletedSnapshot(period, baseDate);
        Optional<RankingSnapshotEntity> previousSnapshot = findLatestCompletedSnapshot(period, previousDate);
        if (currentSnapshot.isEmpty() || previousSnapshot.isEmpty()) {
            return;
        }

        List<RankingEntryEntity> entries = entryRepository.findAllByRankingSnapshotIdIn(List.of(
                currentSnapshot.get().getId(), previousSnapshot.get().getId()
        ));
        Map<Long, RankingEntryEntity> previousEntriesByProfileId = entries.stream()
                .filter(entry -> Objects.equals(entry.getRankingSnapshotId(), previousSnapshot.get().getId()))
                .collect(Collectors.toMap(RankingEntryEntity::getRankingProfileId, Function.identity()));
        List<RankingChange> changes = entries.stream()
                .filter(entry -> Objects.equals(entry.getRankingSnapshotId(), currentSnapshot.get().getId()))
                .map(entry -> changeFor(entry, previousEntriesByProfileId.get(entry.getRankingProfileId())))
                .flatMap(Optional::stream)
                .toList();
        if (changes.isEmpty()) {
            return;
        }

        Map<Long, RankingProfileEntity> profilesById = profileRepository.findAllById(changes.stream()
                        .map(RankingChange::rankingProfileId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(RankingProfileEntity::getId, Function.identity()));
        LocalDateTime sentAt = LocalDateTime.now(clock.withZone(KOREA_ZONE));
        List<Notification> notifications = new ArrayList<>();
        List<NotificationIdentity> identities = new ArrayList<>();
        for (RankingChange change : changes) {
            RankingProfileEntity profile = profilesById.get(change.rankingProfileId());
            if (profile == null) {
                continue;
            }

            storeRepository.findOwnerUserIdByStoreId(profile.getStoreId())
                    .filter(this::isRankingChangeEnabled)
                    .ifPresent(userId -> saveIfAbsent(
                            userId,
                            period,
                            baseDate,
                            currentSnapshot.get(),
                            change,
                            sentAt,
                            notifications,
                            identities
                    ));
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
            publisher.publishAfterCommit(NotificationType.RANKING_CHANGED, identities);
        }
    }

    private Optional<RankingSnapshotEntity> findLatestCompletedSnapshot(
            RankingPeriodCalculator.PeriodPair period,
            LocalDate snapshotDate
    ) {
        return snapshotRepository.findLatestCompletedForDay(
                period.periodStart(),
                period.periodEnd(),
                period.comparisonStart(),
                period.comparisonEnd(),
                snapshotDate.atStartOfDay(KOREA_ZONE)
                        .withZoneSameInstant(snapshotTimestampZone)
                        .toLocalDateTime(),
                snapshotDate.plusDays(1).atStartOfDay(KOREA_ZONE)
                        .withZoneSameInstant(snapshotTimestampZone)
                        .toLocalDateTime()
        );
    }

    private Optional<RankingChange> changeFor(
            RankingEntryEntity currentEntry,
            RankingEntryEntity previousEntry
    ) {
        if (previousEntry == null) {
            return Optional.of(new RankingChange(
                    currentEntry.getRankingProfileId(),
                    currentEntry.getRankNo(),
                    ChangeType.ENTERED,
                    0
            ));
        }
        if (currentEntry.getRankNo() < previousEntry.getRankNo()) {
            return Optional.of(new RankingChange(
                    currentEntry.getRankingProfileId(),
                    currentEntry.getRankNo(),
                    ChangeType.UP,
                    previousEntry.getRankNo() - currentEntry.getRankNo()
            ));
        }
        if (currentEntry.getRankNo() > previousEntry.getRankNo()) {
            return Optional.of(new RankingChange(
                    currentEntry.getRankingProfileId(),
                    currentEntry.getRankNo(),
                    ChangeType.DOWN,
                    currentEntry.getRankNo() - previousEntry.getRankNo()
            ));
        }
        return Optional.empty();
    }

    private boolean isRankingChangeEnabled(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isRankingChangeEnabled)
                .orElse(false);
    }

    private void saveIfAbsent(
            Long userId,
            RankingPeriodCalculator.PeriodPair period,
            LocalDate baseDate,
            RankingSnapshotEntity currentSnapshot,
            RankingChange change,
            LocalDateTime sentAt,
            List<Notification> notifications,
            List<NotificationIdentity> identities
    ) {
        String notificationKey = "RANKING_CHANGED:%d:%s:%s".formatted(
                userId,
                YearMonth.from(period.periodStart()),
                baseDate
        );
        if (notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                userId,
                NotificationType.RANKING_CHANGED,
                notificationKey
        )) {
            return;
        }

        Message message = messageFor(change);
        User user = userRepository.getReferenceById(userId);
        notifications.add(Notification.create(
                user,
                NotificationType.RANKING_CHANGED,
                notificationKey,
                message.title(),
                message.content(),
                RELATED_ENTITY_TYPE,
                currentSnapshot.getId(),
                sentAt,
                sentAt
        ));
        identities.add(new NotificationIdentity(userId, notificationKey));
    }

    private Message messageFor(RankingChange change) {
        return switch (change.changeType()) {
            case UP -> new Message(
                    "성장 랭킹 순위가 올랐어요",
                    "지난 집계보다 %d단계 오른 %d위예요.".formatted(change.rankDifference(), change.rankNo())
            );
            case DOWN -> new Message(
                    "성장 랭킹 순위가 내려갔어요",
                    "지난 집계보다 %d단계 내려갔어요.".formatted(change.rankDifference())
            );
            case ENTERED -> new Message(
                    "성장 랭킹에 처음 진입했어요",
                    "성장 랭킹에 처음 진입했어요."
            );
        };
    }

    private record RankingChange(Long rankingProfileId, int rankNo, ChangeType changeType, int rankDifference) {
    }

    private record Message(String title, String content) {
    }

    private enum ChangeType {
        UP,
        DOWN,
        ENTERED
    }
}
