package com.memme.service.noti;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesUploadReminderService {

    private static final String PREPARE_TITLE = "다음 매출 데이터를 미리 준비해주세요";
    private static final String PREPARE_CONTENT = "예측이 끊기지 않도록 다음 달에도 매출 파일을 업로드해주세요";
    private static final String NORMAL_TITLE = "매출 데이터를 업로드해주세요";
    private static final String NORMAL_CONTENT = "예측이 끊기기 전에 새 매출 파일을 업로드하면 정확한 분석을 계속 받아볼 수 있어요";
    private static final String WARNING_TITLE = "매출 데이터 업로드가 필요해요";
    private static final String WARNING_CONTENT = "예측 종료일이 다가오고 있어요. 새 매출 파일을 업로드해 분석을 계속 받아보세요.";
    private static final String URGENT_TITLE = "매출 데이터 업로드가 임박했어요";
    private static final String URGENT_CONTENT = "곧 예측이 종료돼요. 오늘 매출 파일을 업로드해 분석 공백을 막아주세요.";

    private final StoreRepository storeRepository;
    private final SalesUploadRepository salesUploadRepository;
    private final SalesForecastRepository salesForecastRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SalesUploadReminderService(
            StoreRepository storeRepository,
            SalesUploadRepository salesUploadRepository,
            SalesForecastRepository salesForecastRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.salesUploadRepository = salesUploadRepository;
        this.salesForecastRepository = salesForecastRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public void sendDailyReminders() {
        LocalDate targetDate = LocalDate.now(clock);
        LocalDateTime sentAt = LocalDateTime.now(clock);

        storeRepository.findAllActiveStoreOwners()
                .forEach(storeOwner -> sendReminder(storeOwner, targetDate, sentAt));
    }

    private void sendReminder(
            StoreRepository.ActiveStoreOwner storeOwner,
            LocalDate targetDate,
            LocalDateTime sentAt
    ) {
        Long ownerUserId = storeOwner.getOwnerUserId();
        if (!isReminderEnabled(ownerUserId)) {
            return;
        }

        Optional<SalesUploadEntity> latestUpload = salesUploadRepository
                .findFirstByStoreIdAndStatusOrderByUploadedAtDesc(storeOwner.getStoreId(), SalesUploadStatus.COMPLETED);
        if (latestUpload.isEmpty()) {
            return;
        }

        SalesUploadEntity upload = latestUpload.get();
        Optional<LocalDate> forecastEndDate = salesForecastRepository
                .findForecastEndDateByStoreIdAndBasedOnUploadId(storeOwner.getStoreId(), upload.getId());
        if (forecastEndDate.isEmpty()) {
            return;
        }

        LocalDate endDate = forecastEndDate.get();
        LocalDate forecastStartDate = endDate.minusDays(34);
        LocalDate prepareDate = forecastStartDate.with(TemporalAdjusters.lastDayOfMonth());
        if (targetDate.equals(prepareDate)) {
            saveIfAbsent(ownerUserId, upload.getId(), targetDate, "PREPARE", PREPARE_TITLE, PREPARE_CONTENT, sentAt);
            return;
        }

        if (!targetDate.isAfter(prepareDate) || !targetDate.isBefore(endDate)) {
            return;
        }

        ReminderMessage message = reminderMessage(prepareDate.plusDays(1), endDate, targetDate);
        saveIfAbsent(ownerUserId, upload.getId(), targetDate, "REMINDER", message.title(), message.content(), sentAt);
    }

    private boolean isReminderEnabled(Long ownerUserId) {
        return preferenceRepository.findByUserId(ownerUserId)
                .map(NotificationPreference::isSalesUploadReminderEnabled)
                .orElse(false);
    }

    private ReminderMessage reminderMessage(LocalDate reminderStartDate, LocalDate forecastEndDate, LocalDate targetDate) {
        long totalReminderCount = ChronoUnit.DAYS.between(reminderStartDate, forecastEndDate);
        long reminderIndex = ChronoUnit.DAYS.between(reminderStartDate, targetDate);
        long urgentCount = totalReminderCount >= 6 ? 2 : 1;
        long warningCount = totalReminderCount - 2 - urgentCount;

        if (reminderIndex < 2) {
            return new ReminderMessage(NORMAL_TITLE, NORMAL_CONTENT);
        }
        if (reminderIndex < 2 + warningCount) {
            return new ReminderMessage(WARNING_TITLE, WARNING_CONTENT);
        }
        return new ReminderMessage(URGENT_TITLE, URGENT_CONTENT);
    }

    private void saveIfAbsent(
            Long ownerUserId,
            Long uploadId,
            LocalDate targetDate,
            String phase,
            String title,
            String content,
            LocalDateTime sentAt
    ) {
        String notificationKey = "sales-upload:%d:%s:%s".formatted(uploadId, phase, targetDate);
        if (notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                ownerUserId,
                NotificationType.SALES_UPLOAD_REMINDER,
                notificationKey
        )) {
            return;
        }

        User user = userRepository.getReferenceById(ownerUserId);
        notificationRepository.save(Notification.create(
                user,
                NotificationType.SALES_UPLOAD_REMINDER,
                notificationKey,
                title,
                content,
                "SALES_UPLOAD",
                uploadId,
                sentAt,
                sentAt
        ));
    }

    private record ReminderMessage(String title, String content) {
    }
}
