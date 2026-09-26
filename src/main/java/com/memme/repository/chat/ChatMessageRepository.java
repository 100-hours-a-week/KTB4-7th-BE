package com.memme.repository.chat;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.memme.entity.chat.ChatMessageEntity;
import com.memme.entity.chat.ChatMessageRole;
import com.memme.entity.chat.ChatMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findAllByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            Long userId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<ChatMessageEntity> findAllByUserIdAndStatusOrderByCreatedAtAscIdAsc(
            Long userId,
            ChatMessageStatus status
    );

    boolean existsByUserIdAndRoleAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId,
            ChatMessageRole role,
            Collection<ChatMessageStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<ChatMessageEntity> findByIdAndUserIdAndRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long id,
            Long userId,
            ChatMessageRole role,
            LocalDateTime from,
            LocalDateTime to
    );
}
