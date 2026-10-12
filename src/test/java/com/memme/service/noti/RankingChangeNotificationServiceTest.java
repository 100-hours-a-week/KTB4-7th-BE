package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RankingChangeNotificationServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId SNAPSHOT_STORAGE_ZONE = ZoneId.of("UTC");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-11T23:00:00Z"), KOREA_ZONE
    );
    private static final LocalDateTime PREVIOUS_DAY_START = storageBoundary(LocalDate.of(2026, 10, 11));
    private static final LocalDateTime CURRENT_DAY_START = storageBoundary(LocalDate.of(2026, 10, 12));
    private static final LocalDateTime TWO_DAYS_AGO_START = storageBoundary(LocalDate.of(2026, 10, 10));
    private static final LocalDateTime PREVIOUS_DAY_END = PREVIOUS_DAY_START;

    private RankingSnapshotRepository snapshotRepository;
    private RankingEntryRepository entryRepository;
    private RankingProfileRepository profileRepository;
    private StoreRepository storeRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private RankingChangeNotificationService service;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(RankingSnapshotRepository.class);
        entryRepository = mock(RankingEntryRepository.class);
        profileRepository = mock(RankingProfileRepository.class);
        storeRepository = mock(StoreRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new RankingChangeNotificationService(
                snapshotRepository,
                entryRepository,
                profileRepository,
                storeRepository,
                preferenceRepository,
                notificationRepository,
                userRepository,
                FIXED_CLOCK,
                SNAPSHOT_STORAGE_ZONE
        );
    }

    @Test
    void 어제와_그제의_같은_랭킹_기간을_비교해_수신_동의한_순위_변동만_저장한다() {
        RankingSnapshotEntity current = snapshot(200L);
        RankingSnapshotEntity previous = snapshot(199L);
        givenSnapshotPair(current, previous);
        List<RankingEntryEntity> entries = List.of(
                entry(199L, 1L, 8), entry(200L, 1L, 5),
                entry(199L, 2L, 3), entry(200L, 2L, 7),
                entry(200L, 3L, 20),
                entry(199L, 4L, 7), entry(200L, 4L, 7),
                entry(199L, 5L, 10), entry(200L, 5L, 8),
                entry(199L, 6L, 2)
        );
        when(entryRepository.findAllByRankingSnapshotIdIn(List.of(200L, 199L))).thenReturn(entries);
        List<RankingProfileEntity> profiles = List.of(
                profile(1L, 11L), profile(2L, 12L), profile(3L, 13L), profile(4L, 14L), profile(5L, 15L)
        );
        when(profileRepository.findAllById(any())).thenReturn(profiles);
        givenEnabledOwner(11L, 101L);
        givenEnabledOwner(12L, 102L);
        givenEnabledOwner(13L, 103L);
        givenEnabledOwner(14L, 104L);
        givenDisabledOwner(15L, 105L);
        when(userRepository.getReferenceById(anyLong())).thenAnswer(invocation -> mock(User.class));

        service.createDailyRankingChangeNotifications();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Notification>> notificationsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<Notification> notifications = toList(notificationsCaptor.getValue());
        assertThat(notifications).hasSize(3);
        assertThat(notifications).extracting(Notification::getNotificationKey)
                .containsExactlyInAnyOrder(
                        "RANKING_CHANGED:101:2026-09:2026-10-11",
                        "RANKING_CHANGED:102:2026-09:2026-10-11",
                        "RANKING_CHANGED:103:2026-09:2026-10-11"
                );

        Notification up = notificationFor(notifications, "RANKING_CHANGED:101:2026-09:2026-10-11");
        assertThat(up.getTitle()).isEqualTo("성장 랭킹 순위가 올랐어요");
        assertThat(up.getContent()).isEqualTo("지난 집계보다 3단계 오른 5위예요.");
        assertThat(up.getRelatedEntityType()).isEqualTo("RANKING_SNAPSHOT");
        assertThat(up.getRelatedEntityId()).isEqualTo(200L);
        assertThat(up.getNotificationType()).isEqualTo(NotificationType.RANKING_CHANGED);
        assertThat(up.getSentAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 8, 0));

        Notification down = notificationFor(notifications, "RANKING_CHANGED:102:2026-09:2026-10-11");
        assertThat(down.getTitle()).isEqualTo("성장 랭킹 순위가 내려갔어요");
        assertThat(down.getContent()).isEqualTo("지난 집계보다 4단계 내려갔어요.");

        Notification entered = notificationFor(notifications, "RANKING_CHANGED:103:2026-09:2026-10-11");
        assertThat(entered.getTitle()).isEqualTo("성장 랭킹에 처음 진입했어요");
        assertThat(entered.getContent()).isEqualTo("성장 랭킹에 처음 진입했어요.");

        verify(snapshotRepository).findLatestCompletedForDay(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                PREVIOUS_DAY_START, CURRENT_DAY_START
        );
        verify(snapshotRepository).findLatestCompletedForDay(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                TWO_DAYS_AGO_START, PREVIOUS_DAY_END
        );
    }

    @Test
    void 이전_완료_스냅샷이_없으면_알림을_저장하지_않는다() {
        givenSnapshotPair(snapshot(200L), null);

        service.createDailyRankingChangeNotifications();

        verify(entryRepository, never()).findAllByRankingSnapshotIdIn(any());
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void 현재_완료_스냅샷이_없으면_알림을_저장하지_않는다() {
        givenSnapshotPair(null, snapshot(199L));

        service.createDailyRankingChangeNotifications();

        verify(entryRepository, never()).findAllByRankingSnapshotIdIn(any());
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void 같은_기준일_멱등키의_알림이_이미_있으면_다시_저장하지_않는다() {
        givenSnapshotPair(snapshot(200L), snapshot(199L));
        List<RankingEntryEntity> entries = List.of(
                entry(199L, 1L, 8), entry(200L, 1L, 5)
        );
        when(entryRepository.findAllByRankingSnapshotIdIn(List.of(200L, 199L))).thenReturn(entries);
        List<RankingProfileEntity> profiles = List.of(profile(1L, 11L));
        when(profileRepository.findAllById(any())).thenReturn(profiles);
        givenEnabledOwner(11L, 101L);
        when(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                101L, NotificationType.RANKING_CHANGED, "RANKING_CHANGED:101:2026-09:2026-10-11"
        )).thenReturn(true);

        service.createDailyRankingChangeNotifications();

        verify(notificationRepository, never()).saveAll(any());
    }

    private void givenSnapshotPair(RankingSnapshotEntity current, RankingSnapshotEntity previous) {
        when(snapshotRepository.findLatestCompletedForDay(
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)),
                eq(PREVIOUS_DAY_START), eq(CURRENT_DAY_START)
        )).thenReturn(Optional.ofNullable(current));
        when(snapshotRepository.findLatestCompletedForDay(
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)),
                eq(TWO_DAYS_AGO_START), eq(PREVIOUS_DAY_END)
        )).thenReturn(Optional.ofNullable(previous));
        if (current != null) {
            when(current.getId()).thenReturn(200L);
        }
        if (previous != null) {
            when(previous.getId()).thenReturn(199L);
        }
    }

    private void givenEnabledOwner(Long storeId, Long userId) {
        when(storeRepository.findOwnerUserIdByStoreId(storeId)).thenReturn(Optional.of(userId));
        NotificationPreference preference = mock(NotificationPreference.class);
        when(preference.isRankingChangeEnabled()).thenReturn(true);
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
    }

    private void givenDisabledOwner(Long storeId, Long userId) {
        when(storeRepository.findOwnerUserIdByStoreId(storeId)).thenReturn(Optional.of(userId));
        NotificationPreference preference = mock(NotificationPreference.class);
        when(preference.isRankingChangeEnabled()).thenReturn(false);
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
    }

    private RankingSnapshotEntity snapshot(Long id) {
        RankingSnapshotEntity snapshot = mock(RankingSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(id);
        return snapshot;
    }

    private RankingEntryEntity entry(Long snapshotId, Long profileId, int rankNo) {
        RankingEntryEntity entry = mock(RankingEntryEntity.class);
        when(entry.getRankingSnapshotId()).thenReturn(snapshotId);
        when(entry.getRankingProfileId()).thenReturn(profileId);
        when(entry.getRankNo()).thenReturn(rankNo);
        return entry;
    }

    private RankingProfileEntity profile(Long profileId, Long storeId) {
        RankingProfileEntity profile = mock(RankingProfileEntity.class);
        when(profile.getId()).thenReturn(profileId);
        when(profile.getStoreId()).thenReturn(storeId);
        return profile;
    }

    private Notification notificationFor(List<Notification> notifications, String key) {
        return notifications.stream()
                .filter(notification -> notification.getNotificationKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private List<Notification> toList(Iterable<Notification> notifications) {
        java.util.ArrayList<Notification> result = new java.util.ArrayList<>();
        notifications.forEach(result::add);
        return result;
    }

    private static LocalDateTime storageBoundary(LocalDate koreaDate) {
        return koreaDate.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(SNAPSHOT_STORAGE_ZONE)
                .toLocalDateTime();
    }
}
