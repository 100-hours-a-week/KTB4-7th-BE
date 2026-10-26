package com.memme.repository.noti;

import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.repository.noti.NotificationSseQueryRepository.StoredNotification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationSseQueryRepositoryImpl implements NotificationSseQueryRepository {

    private final EntityManager entityManager;

    public NotificationSseQueryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<StoredNotification> findSseNotificationsByIdentitiesAndType(
            Collection<NotificationIdentity> identities,
            NotificationType notificationType
    ) {
        if (identities.isEmpty()) {
            return List.of();
        }

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<StoredNotification> query = criteriaBuilder.createQuery(StoredNotification.class);
        Root<Notification> notification = query.from(Notification.class);
        Path<Long> userId = notification.get("user").get("id");
        Path<String> notificationKey = notification.get("notificationKey");

        List<Predicate> identityPredicates = identities.stream()
                .map(identity -> criteriaBuilder.and(
                        criteriaBuilder.equal(userId, identity.userId()),
                        criteriaBuilder.equal(notificationKey, identity.notificationKey())
                ))
                .toList();

        query.select(criteriaBuilder.construct(
                StoredNotification.class,
                notification.get("id"),
                userId,
                notification.get("notificationType"),
                notificationKey,
                notification.get("title"),
                notification.get("content"),
                notification.get("relatedEntityType"),
                notification.get("relatedEntityId"),
                notification.get("sentAt"),
                notification.get("readAt")
        )).where(
                criteriaBuilder.equal(notification.get("notificationType"), notificationType),
                criteriaBuilder.or(identityPredicates.toArray(Predicate[]::new))
        );

        return entityManager.createQuery(query).getResultList();
    }
}
