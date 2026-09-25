package com.memme.repository.noti;

import com.memme.entity.noti.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUserIdOrderBySentAtDesc(Long userId);
}
