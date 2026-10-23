package com.memme.repository.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NotificationBulkInsertRepositoryTest {

    @Autowired private NotificationBulkInsertRepository bulkInsertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void 알림을_묶어서_저장하며_기존_필드를_유지한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 8, 0);
        User owner = userRepository.save(User.create(
                "bulk-notification@example.com", "encoded-password", "01012345678", now));
        entityManager.flush();

        int inserted = bulkInsertRepository.insertAll(List.of(
                row(owner.getId(), 10L, now),
                row(owner.getId(), 11L, now)
        ));

        assertThat(inserted).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                SELECT user_id, notification_type, notification_key, title, content,
                       related_entity_type, related_entity_id, sent_at, created_at
                FROM notifications ORDER BY related_entity_id
                """)).hasSize(2)
                .extracting(record -> record.get("notification_key"))
                .containsExactly("solution-ready:10", "solution-ready:11");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE title = ? AND content = ? AND sent_at = ?",
                Integer.class,
                "새 솔루션이 준비되었습니다.", "오늘의 솔루션을 확인해 주세요.", now
        )).isEqualTo(2);
    }

    @Test
    void 오백건을_넘으면_여러_묶음으로_저장한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 8, 0);
        User owner = userRepository.save(User.create(
                "many-bulk-notifications@example.com", "encoded-password", "01098765432", now));
        entityManager.flush();
        List<NotificationBulkInsertRepository.NotificationRow> rows = LongStream.rangeClosed(1, 501)
                .mapToObj(bundleId -> row(owner.getId(), bundleId, now))
                .toList();

        assertThat(bulkInsertRepository.insertAll(rows)).isEqualTo(501);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, owner.getId()
        )).isEqualTo(501);
    }

    private NotificationBulkInsertRepository.NotificationRow row(
            Long userId, Long bundleId, LocalDateTime now
    ) {
        return new NotificationBulkInsertRepository.NotificationRow(
                userId, NotificationType.SOLUTION_READY, "solution-ready:" + bundleId,
                "새 솔루션이 준비되었습니다.", "오늘의 솔루션을 확인해 주세요.",
                "SOLUTION_BUNDLE", bundleId, now, now
        );
    }
}
