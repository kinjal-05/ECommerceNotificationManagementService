package com.notificationservice.services;
import java.time.LocalDateTime;
import java.util.List;

import com.notificationservice.commondtos.*;
import com.notificationservice.dtos.NotificationPatchRequest;
import com.notificationservice.dtos.NotificationRequest;
import com.notificationservice.dtos.NotificationResponse;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface NotificationService {

	NotificationResponse sendNotification(NotificationRequest request);

	NotificationResponse resendNotification(Long id);

	NotificationResponse updateNotification(Long id, NotificationPatchRequest request);

	Page<NotificationResponse> getNotifications(Long userId, Long orderId, Long paymentId, Long shipmentId,
	                                            NotificationStatus status, NotificationType type, LocalDateTime startDate, LocalDateTime endDate,
	                                            Pageable pageable);

	NotificationResponse getById(Long id);

	void handleProductEvent(ProductEvent productEvent);

	void handleCategoryEvent(CategoryEvent categoryEvent);

	void handleUserEmails(UserEmailEvent event);

	void handleUserEmails1(UserEmailEvent event);

	void handlePaymentEvent(PaymentEvent event);

	void handleOrderEvent(OrderEvent event);

	List<NotificationResponse> getInAppNotifications(Long userId);

	void markAsRead(Long id);

	Long getUnreadCount(Long userId);

	void handleLowStockEvent(LowStockEvent event);
}