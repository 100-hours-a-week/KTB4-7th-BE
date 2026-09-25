package com.memme.entity.noti;

import com.memme.entity.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "solution_enabled", nullable = false)
    private boolean solutionEnabled = false;

    @Column(name = "sales_upload_reminder_enabled", nullable = false)
    private boolean salesUploadReminderEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NotificationPreference() {
    }

    public static NotificationPreference create(User user, LocalDateTime createdAt) {
        NotificationPreference preference = new NotificationPreference();
        preference.user = user;
        preference.createdAt = createdAt;
        preference.updatedAt = createdAt;
        return preference;
    }

    public boolean isSolutionEnabled() {
        return solutionEnabled;
    }

    public boolean isSalesUploadReminderEnabled() {
        return salesUploadReminderEnabled;
    }

    public void updatePreferences(
            Boolean solutionEnabled,
            Boolean salesUploadReminderEnabled,
            LocalDateTime updatedAt
    ) {
        if (solutionEnabled != null) {
            this.solutionEnabled = solutionEnabled;
        }
        if (salesUploadReminderEnabled != null) {
            this.salesUploadReminderEnabled = salesUploadReminderEnabled;
        }
        this.updatedAt = updatedAt;
    }
}
