package com.memme.repository.noti;

import com.memme.entity.noti.NotificationType;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationBulkInsertRepository {

    private static final int INSERT_BATCH_SIZE = 500;
    private static final String INSERT_PREFIX = """
            INSERT INTO notifications (
                user_id, notification_type, notification_key, title, content,
                related_entity_type, related_entity_id, sent_at, created_at
            ) VALUES
            """;
    private static final String ROW_PLACEHOLDERS = "(?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public static int insertStatementCount(int rowCount) {
        if (rowCount <= 0) {
            return 0;
        }
        return (rowCount + INSERT_BATCH_SIZE - 1) / INSERT_BATCH_SIZE;
    }

    public NotificationBulkInsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertAll(List<NotificationRow> notifications) {
        int totalInserted = 0;
        for (int start = 0; start < notifications.size(); start += INSERT_BATCH_SIZE) {
            List<NotificationRow> batch = notifications.subList(
                    start, Math.min(start + INSERT_BATCH_SIZE, notifications.size())
            );
            String sql = INSERT_PREFIX + String.join(", ", Collections.nCopies(batch.size(), ROW_PLACEHOLDERS));
            int inserted = jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                int parameter = 1;
                for (NotificationRow notification : batch) {
                    statement.setLong(parameter++, notification.userId());
                    statement.setString(parameter++, notification.notificationType().name());
                    statement.setString(parameter++, notification.notificationKey());
                    statement.setString(parameter++, notification.title());
                    statement.setString(parameter++, notification.content());
                    statement.setString(parameter++, notification.relatedEntityType());
                    statement.setLong(parameter++, notification.relatedEntityId());
                    statement.setObject(parameter++, notification.sentAt());
                    statement.setObject(parameter++, notification.createdAt());
                }
                return statement;
            });
            if (inserted != batch.size()) {
                throw new IllegalStateException("알림 벌크 저장 건수가 요청 건수와 다릅니다.");
            }
            totalInserted += inserted;
        }
        return totalInserted;
    }

    public record NotificationRow(
            Long userId,
            NotificationType notificationType,
            String notificationKey,
            String title,
            String content,
            String relatedEntityType,
            Long relatedEntityId,
            LocalDateTime sentAt,
            LocalDateTime createdAt
    ) {
    }
}
