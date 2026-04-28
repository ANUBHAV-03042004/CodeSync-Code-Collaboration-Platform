package com.notificationservice.codesync.repository;

import com.notificationservice.codesync.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId);

    int countByRecipientIdAndIsReadFalse(Long recipientId);

    List<Notification> findByType(Notification.NotificationType type);

    List<Notification> findByRelatedId(String relatedId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :uid AND n.isRead = false")
    int markAllReadByRecipient(@Param("uid") Long recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipientId = :uid AND n.isRead = true")
    int deleteReadByRecipient(@Param("uid") Long recipientId);
}