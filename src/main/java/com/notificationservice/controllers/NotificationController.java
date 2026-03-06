package com.notificationservice.controllers;
import java.time.LocalDateTime;
import java.util.List; // ✅ add this

import com.notificationservice.dtos.NotificationPatchRequest;
import com.notificationservice.dtos.NotificationRequest;
import com.notificationservice.dtos.NotificationResponse;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import com.notificationservice.services.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@PostMapping
	public ResponseEntity<NotificationResponse> sendNotification(@Valid @RequestBody NotificationRequest request) {

		NotificationResponse response = notificationService.sendNotification(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/{id}/resend")
	public ResponseEntity<NotificationResponse> resendNotification(@PathVariable Long id) {

		NotificationResponse response = notificationService.resendNotification(id);
		return ResponseEntity.ok(response);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<NotificationResponse> updateNotification(@PathVariable Long id,
	                                                               @RequestBody NotificationPatchRequest request) {

		NotificationResponse response = notificationService.updateNotification(id, request);
		return ResponseEntity.ok(response);
	}

	@GetMapping
	public ResponseEntity<Page<NotificationResponse>> getNotifications(

			@RequestParam(required = false) Long userId, @RequestParam(required = false) Long orderId,
			@RequestParam(required = false) Long paymentId, @RequestParam(required = false) Long shipmentId,

			@RequestParam(required = false) NotificationStatus status,
			@RequestParam(required = false) NotificationType type,

			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,

			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "createdAt,desc") String[] sort) {

		Sort sorting = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);

		Pageable pageable = PageRequest.of(page, size, sorting);

		Page<NotificationResponse> result = notificationService.getNotifications(userId, orderId, paymentId, shipmentId,
				status, type, startDate, endDate, pageable);

		return ResponseEntity.ok(result);
	}

	@GetMapping("/{id}")
	public ResponseEntity<NotificationResponse> getById(@PathVariable Long id) {
		return ResponseEntity.ok(notificationService.getById(id));
	}

	@GetMapping("/inApp")
	public ResponseEntity<List<NotificationResponse>> getInAppNotifications(
			@RequestHeader("X-USER-LONG-ID") Long userId) {
		return ResponseEntity.ok(notificationService.getInAppNotifications(userId));
	}

	@PatchMapping("/markRead/{id}")
	public ResponseEntity<String> markAsRead(@PathVariable Long id) {
		notificationService.markAsRead(id);
		return ResponseEntity.ok("Notification marked as read");
	}

	@GetMapping("/unreadCount")
	public ResponseEntity<Long> getUnreadCount(@RequestHeader("X-USER-LONG-ID") Long userId) {
		return ResponseEntity.ok(notificationService.getUnreadCount(userId));
	}

}
