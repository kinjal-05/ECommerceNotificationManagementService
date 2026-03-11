package com.notificationservice.servicesImpl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import com.notificationservice.commondtos.*;
import com.notificationservice.dtos.NotificationPatchRequest;
import com.notificationservice.dtos.NotificationRequest;
import com.notificationservice.dtos.NotificationResponse;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import com.notificationservice.exceptions.BadRequestException;
import com.notificationservice.exceptions.ResourceNotFoundException;
import com.notificationservice.feignClient.UserFeignClient;
import com.notificationservice.models.Notification;
import com.notificationservice.repositories.NotificationRepository;
import com.notificationservice.services.NotificationService;
import com.notificationservice.specifications.NotificationSpecification;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

	private final NotificationRepository notificationRepository;
	private final UserFeignClient userFeignClient;
	private final StreamBridge streamBridge;

	private Map<Long, ProductEvent> requestMap = new HashMap<>();
	private Map<Long, CategoryEvent> requestMap1 = new HashMap<>();

	@Override
	public NotificationResponse sendNotification(NotificationRequest request) {
		String subject = request.getSubject();
		String message = request.getMessage();
		if (message == null || message.isBlank()) {
			throw new BadRequestException("Message cannot be empty");
		}
		Notification notification = Notification.builder().userId(request.getUserId()).orderId(request.getOrderId())
				.paymentId(request.getPaymentId()).shipmentId(request.getShipmentId())
				.notificationType(request.getNotificationType()).subject(subject).message(message)
				.status(NotificationStatus.PENDING).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
				.build();
		try {
			send(notification);
			notification.setStatus(NotificationStatus.SENT);
			notification.setSentAt(LocalDateTime.now());
		} catch (Exception e) {
			notification.setStatus(NotificationStatus.FAILED);
			notification.setRetryCount(1);
		}
		Notification saved = notificationRepository.save(notification);
		return new NotificationResponse(saved.getId(), saved.getUserId(), saved.getMessage(), saved.getSubject(),
				saved.getStatus().name(), saved.getSentAt());
	}

	@Override
	public NotificationResponse resendNotification(Long id) {

		Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
		int retryCount = notification.getRetryCount() == null ? 0 : notification.getRetryCount();
		notification.setRetryCount(retryCount + 1);
		notification.setUpdatedAt(LocalDateTime.now());
		try {
			send(notification);
			notification.setStatus(NotificationStatus.SENT);
			notification.setSentAt(LocalDateTime.now());
		} catch (Exception e) {
			notification.setStatus(NotificationStatus.FAILED);
		}
		Notification updated = notificationRepository.save(notification);
		return new NotificationResponse(updated.getId(), updated.getUserId(), updated.getMessage(),
				updated.getSubject(), updated.getStatus().name(), updated.getSentAt());
	}

	@Override
	public NotificationResponse updateNotification(Long id, NotificationPatchRequest request) {
		Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
		boolean updated = false;
		if (request.getStatus() != null) {
			notification.setStatus(request.getStatus());
			if (request.getStatus() == NotificationStatus.SENT) {
				notification.setSentAt(LocalDateTime.now());
			}
			updated = true;
		}
		if (request.getMessage() != null) {
			if (request.getMessage().isBlank()) {
				throw new BadRequestException("Message cannot be empty");
			}
			notification.setMessage(request.getMessage());
			updated = true;
		}
		if (request.getSubject() != null) {
			notification.setSubject(request.getSubject());
			updated = true;
		}
		if (!updated) {
			throw new BadRequestException("No valid fields provided for update");
		}
		notification.setUpdatedAt(LocalDateTime.now());
		Notification updatedNotification = notificationRepository.save(notification);
		return new NotificationResponse(updatedNotification.getId(), updatedNotification.getUserId(),
				updatedNotification.getMessage(), updatedNotification.getSubject(),
				updatedNotification.getStatus().name(), updatedNotification.getSentAt());
	}

	@Override
	public Page<NotificationResponse> getNotifications(Long userId, Long orderId, Long paymentId, Long shipmentId,
	                                                   NotificationStatus status, NotificationType type, LocalDateTime startDate, LocalDateTime endDate,
	                                                   Pageable pageable) {
		var spec = NotificationSpecification.filter(userId, orderId, paymentId, shipmentId, status, type, startDate,
				endDate);
		return notificationRepository.findAll(spec, pageable).map(this::mapToResponse);
	}

	@Override
	public NotificationResponse getById(Long id) {
		Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
		return mapToResponse(notification);
	}

	@Override
	public List<NotificationResponse> getInAppNotifications(Long userId) {
		return notificationRepository.findByUserIdAndNotificationType(userId, NotificationType.IN_APP).stream()
				.map(this::mapToResponse).collect(Collectors.toList());
	}

	@Override
	public void markAsRead(Long id) {
		Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
		notification.setStatus(NotificationStatus.READ);
		notification.setUpdatedAt(LocalDateTime.now());
		notificationRepository.save(notification);

	}

	@Override
	public Long getUnreadCount(Long userId) {
		return notificationRepository.countByUserIdAndNotificationTypeAndStatus(userId, NotificationType.IN_APP,
				NotificationStatus.SENT);
	}

	@Override
	public void handleProductEvent(ProductEvent productEvent) {

		Long requestId = ThreadLocalRandom.current().nextLong();
		requestMap.put(requestId, productEvent);
		UserFetchRequestEvent request = new UserFetchRequestEvent();
		request.setRequestId(requestId);
		streamBridge.send("userFetch-out-0", request);

	}

	@Override
	public void handleCategoryEvent(CategoryEvent categoryEvent) {
		Long requestId = ThreadLocalRandom.current().nextLong();
		requestMap1.put(requestId, categoryEvent);
		UserFetchRequestEvent request = new UserFetchRequestEvent();
		request.setRequestId(requestId);
		streamBridge.send("userFetchForCategory-out-0", request);
	}

	@Override
	public void handleUserEmails(UserEmailEvent event) {
		ProductEvent product = requestMap.get(event.getRequestId());
		CategoryEvent categoryEvent = requestMap1.get(event.getRequestId());
		if (product == null && categoryEvent == null) {
			System.out.println("❌ No ProductEvent found for requestId: " + event.getRequestId());
			return;
		}
		for (UserEmailDto user : event.getUsers()) {
			try {
				if (product != null) {
					sendEmail(user.getUserId(), user.getEmail(), product);
				}
				if (categoryEvent != null) {
					sendEmail1(user.getUserId(), user.getEmail(), categoryEvent);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		requestMap.remove(event.getRequestId());
		requestMap1.remove(event.getRequestId());

	}

	@Override
	public void handleUserEmails1(UserEmailEvent event) {
		CategoryEvent categoryEvent = requestMap1.get(event.getRequestId());
		if (categoryEvent == null) {
			System.out.println("❌ No CategoryEvent found for requestId: " + event.getRequestId());
			return;
		}
		for (UserEmailDto user : event.getUsers()) {
			try {
				sendEmail1(user.getUserId(), user.getEmail(), categoryEvent);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		requestMap1.remove(event.getRequestId());

	}

	@Override
	public void handlePaymentEvent(PaymentEvent event) {
		sendPaymentEmail(event.getUserId(), event);
	}

	@Override
	public void handleOrderEvent(OrderEvent event) {

		try {
			UserEmailDto user = userFeignClient.getUser1ById(event.getUserId());
			switch (event.getStatus()) {
				case "CONFIRMED":
					saveEmailNotification(user, event);
					saveSmsNotification(user, event);
					savePushNotification(user, event);
					saveInAppNotification(user, event);
					break;

				case "CANCELLED":
					saveEmailNotification(user, event);
					saveSmsNotification(user, event);
					savePushNotification(user, event);
					saveInAppNotification(user, event);
					break;

				case "SHIPPED":

					saveShippedNotification(user, event);
					break;

				case "DELIVERED":

					saveDeliveredNotification(user, event);
					break;

				default:
					System.out.println("⚠️ Unknown status: " + event.getStatus());
			}

		} catch (Exception e) {
			System.out.println("❌ Error: " + e.getMessage());
		}
	}

	private void saveShippedNotification(UserEmailDto user, OrderEvent event) {

		String subject = "Order Shipped - Order #" + event.getOrderId();
		String message = "Dear " + user.getEmail() + ",\n\n" + "Your order #" + event.getOrderId()
				+ " has been shipped!\n\n" + "You will receive it in 2-3 business days.\n\n"
				+ "Thank you for shopping with us!";

		Notification email = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.EMAIL).subject(subject).message(message)
				.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(email);

		Notification push = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.PUSH).subject("Order Shipped 🚚")
				.message("Order #" + event.getOrderId() + " is on its way!").status(NotificationStatus.SENT)
				.createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(push);

		Notification inApp = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.IN_APP).subject("Order #" + event.getOrderId() + " Shipped")
				.message("Your order is on its way! " + "Expected delivery in 2-3 business days.")
				.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(inApp);

	}

	private void saveDeliveredNotification(UserEmailDto user, OrderEvent event) {

		String subject = "Order Delivered - Order #" + event.getOrderId();
		String message = "Dear " + user.getEmail() + ",\n\n" + "Your order #" + event.getOrderId()
				+ " has been delivered!\n\n" + "We hope you enjoy your purchase.\n\n"
				+ "Thank you for shopping with us!";

		Notification email = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.EMAIL).subject(subject).message(message)
				.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(email);

		Notification push = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.PUSH).subject("Order Delivered ✅")
				.message("Order #" + event.getOrderId() + " has been delivered!").status(NotificationStatus.SENT)
				.createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(push);

		Notification inApp = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.IN_APP).subject("Order #" + event.getOrderId() + " Delivered")
				.message("Your order has been delivered successfully!").status(NotificationStatus.SENT)
				.createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(inApp);

	}

	private void send(Notification notification) {
		System.out.println("📤 Sending " + notification.getNotificationType() + " to user " + notification.getUserId());
	}

	private String processTemplate(String body, Long userId) {
		return body.replace("{userId}", String.valueOf(userId));
	}

	private void sendEmail(Long userId, String email, ProductEvent product) {
		Notification notification = new Notification(userId, NotificationType.EMAIL,
				"New Product: " + product.getTitle(), "New Product Added!\n\n" + "Title: " + product.getTitle() + "\n"
				+ "Author: " + product.getAuthor() + "\n" + "Price: ₹" + product.getPrice());
		notificationRepository.save(notification);

	}

	private void sendEmail1(Long userId, String email, CategoryEvent categoryEvent) {
		Notification notification = new Notification(userId, NotificationType.EMAIL,
				"New Category: " + categoryEvent.getName(),
				"New Category Added!\n\n" + "Category: " + categoryEvent.getName());
		notificationRepository.save(notification);

	}

	private void sendPaymentEmail(Long userId, PaymentEvent event) {

		String subject;
		String message;

		if ("SUCCESS".equals(event.getStatus())) {
			subject = "Payment Successful - Order #" + event.getOrderId();
			message = "Dear Customer,\n\n" + "Your payment was successful!\n\n" + "Order ID      : "
					+ event.getOrderId() + "\n" + "Amount        : ₹" + event.getAmount() + "\n" + "Payment Method: "
					+ event.getMethod() + "\n" + "Transaction ID: " + event.getTransactionId() + "\n\n"
					+ "Thank you for shopping with us!";
		} else {
			subject = "Payment Refunded - Order #" + event.getOrderId();
			message = "Dear Customer,\n\n" + "Your payment has been refunded!\n\n" + "Order ID      : "
					+ event.getOrderId() + "\n" + "Amount        : ₹" + event.getAmount() + "\n" + "Transaction ID: "
					+ event.getTransactionId() + "\n\n" + "Refund will reflect in 3-5 business days.";
		}

		Notification notification = new Notification(userId, NotificationType.EMAIL, subject, message);
		notificationRepository.save(notification);

	}

	private void saveEmailNotification(UserEmailDto user, OrderEvent event) {

		String subject;
		String message;

		if ("CANCELLED".equals(event.getStatus())) {
			subject = "Order Cancelled - Order #" + event.getOrderId();
			message = "Dear " + user.getEmail() + ",\n\n" + "Your order #" + event.getOrderId()
					+ " has been cancelled.\n" + "Amount : ₹" + event.getTotalAmount() + "\n" + "Reason : "
					+ event.getReason() + "\n\n" + "Refund will be processed in 3-5 business days.";
		} else {
			subject = "Order Confirmed - Order #" + event.getOrderId();
			message = "Dear " + user.getEmail() + ",\n\n" + "Your order #" + event.getOrderId() + " is confirmed!\n"
					+ "Amount  : ₹" + event.getTotalAmount() + "\n" + "Address : " + event.getShippingAddress() + "\n\n"
					+ "Thank you for shopping with us!";
		}

		Notification notification = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.EMAIL).subject(subject).message(message)
				.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(notification);

	}

	private void saveSmsNotification(UserEmailDto user, OrderEvent event) {

		String message;

		if ("CANCELLED".equals(event.getStatus())) {
			message = "Your order #" + event.getOrderId() + " has been cancelled. Amount ₹" + event.getTotalAmount()
					+ " will be refunded in 3-5 days.";
		} else {
			message = "Your order #" + event.getOrderId() + " is confirmed! Amount: ₹" + event.getTotalAmount()
					+ ". Thank you for shopping!";
		}

		Notification notification = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.SMS).message(message).status(NotificationStatus.SENT)
				.createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(notification);

	}

	private void savePushNotification(UserEmailDto user, OrderEvent event) {

		String title;
		String message;

		if ("CANCELLED".equals(event.getStatus())) {
			title = "Order Cancelled ❌";
			message = "Order #" + event.getOrderId() + " cancelled. ₹" + event.getTotalAmount() + " will be refunded.";
		} else {
			title = "Order Confirmed ✅";
			message = "Order #" + event.getOrderId() + " confirmed! Amount: ₹" + event.getTotalAmount();
		}

		Notification notification = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.PUSH).subject(title).message(message).status(NotificationStatus.SENT)
				.createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(notification);

	}

	private void saveInAppNotification(UserEmailDto user, OrderEvent event) {

		String title;
		String message;

		if ("CANCELLED".equals(event.getStatus())) {
			title = "Order #" + event.getOrderId() + " Cancelled";
			message = "Your order has been cancelled. Reason: " + event.getReason() + ". Amount ₹"
					+ event.getTotalAmount() + " will be refunded in 3-5 business days.";
		} else {
			title = "Order #" + event.getOrderId() + " Confirmed";
			message = "Your order is confirmed and will be delivered to " + event.getShippingAddress() + ". Amount: ₹"
					+ event.getTotalAmount();
		}

		Notification notification = Notification.builder().userId(user.getUserId()).orderId(event.getOrderId())
				.notificationType(NotificationType.IN_APP).subject(title).message(message)
				.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(notification);

	}

	private NotificationResponse mapToResponse(Notification n) {
		return new NotificationResponse(n.getId(), n.getUserId(), n.getMessage(), n.getSubject(), n.getStatus().name(),
				n.getSentAt());
	}

	@Override
	public void handleLowStockEvent(LowStockEvent event) {

		Notification notification = Notification.builder().userId(1L).notificationType(NotificationType.IN_APP)
				.subject("Low Stock Alert - Product #" + event.getProductId())
				.message("⚠️ Low Stock Alert!\n\n" + "Product ID : " + event.getProductId() + "\n" + "Available  : "
						+ event.getAvailableQuantity() + " units\n" + "Please restock immediately.")
				.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build();
		notificationRepository.save(notification);

	}

	@CircuitBreaker(name = "userService", fallbackMethod = "userFallback")
	public UserEmailDto getUserWithCircuitBreaker(Long userId) {

		return userFeignClient.getUser1ById(userId);
	}

	public UserEmailDto userFallback(Long userId, Throwable ex) {

		UserEmailDto user = new UserEmailDto();
		user.setUserId(userId);
		user.setEmail("fallback-user@example.com");

		return user;
	}
}