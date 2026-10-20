package com.memme.repository.noti;

import com.memme.entity.noti.NotificationPreference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserId(Long userId);

    @Query("""
            select preference.user.id as userId, preference.solutionEnabled as solutionEnabled
            from NotificationPreference preference
            where preference.user.id in :userIds
            """)
    List<SolutionPreferenceSetting> findSolutionPreferenceSettingsByUserIds(
            @Param("userIds") Collection<Long> userIds
    );

    interface SolutionPreferenceSetting {

        Long getUserId();

        Boolean getSolutionEnabled();
    }
}
