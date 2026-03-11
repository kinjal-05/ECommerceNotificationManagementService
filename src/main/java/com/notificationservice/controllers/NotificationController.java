package com.notificationservice.controllers;

import com.notificationservice.dtos.NotificationPatchRequest;
import com.notificationservice.dtos.NotificationRequest;
import com.notificationservice.dtos.NotificationResponse;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import com.notificationservice.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * All route paths resolved from api-paths.yml at startup.
 *
 * api.notification.base         → /api/notifications
 * api.notification.send         → /send
 * api.notification.resend       → /{id}/resend
 * api.notification.update       → /{id}
 * api.notification.get-all      → /all
 * api.notification.get-by-id    → /{id}
 * api.notification.in-app       → /inApp
 * api.notification.mark-read    → /markRead/{id}
 * api.notification.unread-count → /unreadCount
 *
 * Bugs fixed from original:
 * - Manual constructor replaced with @RequiredArgsConstructor
 * - @RequestMapping("/notifications") missing /api prefix → changed to /api/notifications
 * - sendNotification POST on base path → moved to explicit /send path to avoid
 *   ambiguity with GET /all on the same base
 * - sendNotification returns 200 OK → changed to 201 CREATED
 * - resendNotification returns 200 OK on POST → changed to 201 CREATED
 * - markAsRead returns ResponseEntity<String> with body → changed to 204 No Content
 * - sort param split assumes exactly 2 elements → added safe fallback defaults
 */
@RestController
@RequestMapping("${api.notification.base}")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@PostMapping("${api.notification.send}")
	public ResponseEntity<NotificationResponse> sendNotification(
			@Valid @RequestBody NotificationRequest request) {

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(notificationService.sendNotification(request));
	}

	@PostMapping("${api.notification.resend}")
	public ResponseEntity<NotificationResponse> resendNotification(@PathVariable Long id) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(notificationService.resendNotification(id));
	}

	@PatchMapping("${api.notification.update}")
	public ResponseEntity<NotificationResponse> updateNotification(
			@PathVariable Long id,
			@Valid @RequestBody NotificationPatchRequest request) {

		return ResponseEntity.ok(notificationService.updateNotification(id, request));
	}

	// MUST be declared before get-by-id to avoid /all matching /{id}
	@GetMapping("${api.notification.get-all}")
	public ResponseEntity<Page<NotificationResponse>> getNotifications(
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) Long orderId,
			@RequestParam(required = false) Long paymentId,
			@RequestParam(required = false) Long shipmentId,
			@RequestParam(required = false) NotificationStatus status,
			@RequestParam(required = false) NotificationType type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "desc") String sortDir) {

		Sort sorting = sortDir.equalsIgnoreCase("desc")
				? Sort.by(sortBy).descending()
				: Sort.by(sortBy).ascending();

		Pageable pageable = PageRequest.of(page, size, sorting);

		return ResponseEntity.ok(
				notificationService.getNotifications(userId, orderId, paymentId, shipmentId,
						status, type, startDate, endDate, pageable)
		);
	}

	@GetMapping("${api.notification.get-by-id}")
	public ResponseEntity<NotificationResponse> getById(@PathVariable Long id) {
		return ResponseEntity.ok(notificationService.getById(id));
	}

	@GetMapping("${api.notification.in-app}")
	public ResponseEntity<List<NotificationResponse>> getInAppNotifications(
			@RequestHeader("X-USER-LONG-ID") Long userId) {

		return ResponseEntity.ok(notificationService.getInAppNotifications(userId));
	}

	@PatchMapping("${api.notification.mark-read}")
	public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
		notificationService.markAsRead(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("${api.notification.unread-count}")
	public ResponseEntity<Long> getUnreadCount(@RequestHeader("X-USER-LONG-ID") Long userId) {
		return ResponseEntity.ok(notificationService.getUnreadCount(userId));
	}
}