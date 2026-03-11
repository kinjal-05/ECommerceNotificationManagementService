package com.notificationservice.repositories;

import java.util.List;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import com.notificationservice.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRepository
		extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

	List<Notification> findByUserIdAndNotificationType(Long userId, NotificationType notificationType);

	Long countByUserIdAndNotificationTypeAndStatus(Long userId, NotificationType notificationType,
	                                               NotificationStatus status);

	List<Notification> findByUserId(Long userId);
}
