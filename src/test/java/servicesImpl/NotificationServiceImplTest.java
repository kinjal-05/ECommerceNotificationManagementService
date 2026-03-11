package servicesImpl;

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
import com.notificationservice.servicesImpl.NotificationServiceImpl;
import com.notificationservice.specifications.NotificationSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl Tests")
class NotificationServiceImplTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private UserFeignClient userFeignClient;

	@Mock
	private StreamBridge streamBridge;

	@InjectMocks
	private NotificationServiceImpl notificationService;

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────────

	private Notification buildNotification(Long id, Long userId, NotificationType type,
	                                       String subject, String message, NotificationStatus status) {
		Notification n = new Notification();
		n.setId(id);
		n.setUserId(userId);
		n.setNotificationType(type);
		n.setSubject(subject);
		n.setMessage(message);
		n.setStatus(status);
		n.setCreatedAt(LocalDateTime.now());
		n.setUpdatedAt(LocalDateTime.now());
		return n;
	}

	private NotificationRequest buildRequest(Long userId, String message,
	                                         String subject, NotificationType type) {
		NotificationRequest req = new NotificationRequest();
		req.setUserId(userId);
		req.setMessage(message);
		req.setSubject(subject);
		req.setNotificationType(type);
		return req;
	}

	private UserEmailDto buildUser(Long userId, String email) {
		UserEmailDto u = new UserEmailDto();
		u.setUserId(userId);
		u.setEmail(email);
		return u;
	}

	// =========================================================================
	// sendNotification
	// =========================================================================

	@Nested
	@DisplayName("sendNotification()")
	class SendNotification {

		@Test
		@DisplayName("Valid request → saves and returns SENT notification")
		void validRequest_returnsSentNotification() {
			NotificationRequest request = buildRequest(1L, "Hello!", "Subject", NotificationType.EMAIL);

			Notification saved = buildNotification(10L, 1L, NotificationType.EMAIL,
					"Subject", "Hello!", NotificationStatus.SENT);
			saved.setSentAt(LocalDateTime.now());

			when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

			NotificationResponse response = notificationService.sendNotification(request);

			assertThat(response.getId()).isEqualTo(10L);
			assertThat(response.getUserId()).isEqualTo(1L);
			assertThat(response.getMessage()).isEqualTo("Hello!");
			assertThat(response.getSubject()).isEqualTo("Subject");
			assertThat(response.getStatus()).isEqualTo("SENT");

			verify(notificationRepository).save(any(Notification.class));
		}

		@Test
		@DisplayName("Null message → BadRequestException")
		void nullMessage_throwsBadRequest() {
			NotificationRequest request = buildRequest(1L, null, "Subject", NotificationType.EMAIL);

			assertThatThrownBy(() -> notificationService.sendNotification(request))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("Message cannot be empty");

			verifyNoInteractions(notificationRepository);
		}

		@Test
		@DisplayName("Blank message → BadRequestException")
		void blankMessage_throwsBadRequest() {
			NotificationRequest request = buildRequest(1L, "   ", "Subject", NotificationType.EMAIL);

			assertThatThrownBy(() -> notificationService.sendNotification(request))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("Message cannot be empty");

			verifyNoInteractions(notificationRepository);
		}

		@Test
		@DisplayName("Saved notification has SENT status and sentAt set")
		void savedNotification_hasSentStatusAndSentAt() {
			NotificationRequest request = buildRequest(1L, "Test msg", "Subj", NotificationType.SMS);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			Notification saved = buildNotification(1L, 1L, NotificationType.SMS,
					"Subj", "Test msg", NotificationStatus.SENT);
			saved.setSentAt(LocalDateTime.now());

			when(notificationRepository.save(captor.capture())).thenReturn(saved);

			notificationService.sendNotification(request);

			Notification captured = captor.getValue();
			assertThat(captured.getStatus()).isEqualTo(NotificationStatus.SENT);
			assertThat(captured.getSentAt()).isNotNull();
		}
	}

	// =========================================================================
	// resendNotification
	// =========================================================================

	@Nested
	@DisplayName("resendNotification()")
	class ResendNotification {

		@Test
		@DisplayName("Existing notification → resent and returns SENT")
		void existingNotification_resent() {
			Notification existing = buildNotification(1L, 2L, NotificationType.EMAIL,
					"Subject", "Message", NotificationStatus.FAILED);

			Notification saved = buildNotification(1L, 2L, NotificationType.EMAIL,
					"Subject", "Message", NotificationStatus.SENT);
			saved.setSentAt(LocalDateTime.now());

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

			NotificationResponse response = notificationService.resendNotification(1L);

			assertThat(response.getStatus()).isEqualTo("SENT");
			verify(notificationRepository).save(any(Notification.class));
		}

		@Test
		@DisplayName("Non-existent notification → ResourceNotFoundException")
		void notFound_throwsException() {
			when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> notificationService.resendNotification(99L))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Notification not found with id: 99");
		}

		@Test
		@DisplayName("Resend updates updatedAt")
		void resend_updatesUpdatedAt() {
			Notification existing = buildNotification(1L, 2L, NotificationType.PUSH,
					"Sub", "Msg", NotificationStatus.FAILED);
			LocalDateTime before = existing.getUpdatedAt();

			Notification saved = buildNotification(1L, 2L, NotificationType.PUSH,
					"Sub", "Msg", NotificationStatus.SENT);
			saved.setSentAt(LocalDateTime.now());

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(notificationRepository.save(any())).thenReturn(saved);

			notificationService.resendNotification(1L);

			// updatedAt should be set to now (after the existing value)
			assertThat(existing.getUpdatedAt()).isAfterOrEqualTo(before);
		}
	}

	// =========================================================================
	// updateNotification
	// =========================================================================

	@Nested
	@DisplayName("updateNotification()")
	class UpdateNotification {

		@Test
		@DisplayName("Update status only → saved with new status")
		void updateStatusOnly() {
			Notification existing = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.PENDING);

			Notification saved = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.SENT);
			saved.setSentAt(LocalDateTime.now());

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(notificationRepository.save(any())).thenReturn(saved);

			NotificationPatchRequest patch = new NotificationPatchRequest();
			patch.setStatus(NotificationStatus.SENT);

			NotificationResponse response = notificationService.updateNotification(1L, patch);

			assertThat(response.getStatus()).isEqualTo("SENT");
			assertThat(response.getSentAt()).isNotNull();
		}

		@Test
		@DisplayName("Update message only → saved with new message")
		void updateMessageOnly() {
			Notification existing = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "OldMsg", NotificationStatus.PENDING);

			Notification saved = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "NewMsg", NotificationStatus.PENDING);

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(notificationRepository.save(any())).thenReturn(saved);

			NotificationPatchRequest patch = new NotificationPatchRequest();
			patch.setMessage("NewMsg");

			NotificationResponse response = notificationService.updateNotification(1L, patch);

			assertThat(response.getMessage()).isEqualTo("NewMsg");
		}

		@Test
		@DisplayName("Update subject only → saved with new subject")
		void updateSubjectOnly() {
			Notification existing = buildNotification(1L, 1L, NotificationType.EMAIL,
					"OldSubject", "Msg", NotificationStatus.PENDING);

			Notification saved = buildNotification(1L, 1L, NotificationType.EMAIL,
					"NewSubject", "Msg", NotificationStatus.PENDING);

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(notificationRepository.save(any())).thenReturn(saved);

			NotificationPatchRequest patch = new NotificationPatchRequest();
			patch.setSubject("NewSubject");

			NotificationResponse response = notificationService.updateNotification(1L, patch);

			assertThat(response.getSubject()).isEqualTo("NewSubject");
		}

		@Test
		@DisplayName("Blank message → BadRequestException")
		void blankMessage_throwsBadRequest() {
			Notification existing = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.PENDING);

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));

			NotificationPatchRequest patch = new NotificationPatchRequest();
			patch.setMessage("   ");

			assertThatThrownBy(() -> notificationService.updateNotification(1L, patch))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("Message cannot be empty");
		}

		@Test
		@DisplayName("No valid fields provided → BadRequestException")
		void noFields_throwsBadRequest() {
			Notification existing = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.PENDING);

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));

			NotificationPatchRequest patch = new NotificationPatchRequest();
			// all fields null

			assertThatThrownBy(() -> notificationService.updateNotification(1L, patch))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("No valid fields provided for update");
		}

		@Test
		@DisplayName("Notification not found → ResourceNotFoundException")
		void notFound_throwsException() {
			when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> notificationService.updateNotification(99L, new NotificationPatchRequest()))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Notification not found with id: 99");
		}

		@Test
		@DisplayName("Status = SENT → sentAt is set on the notification")
		void statusSent_setsSentAt() {
			Notification existing = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.PENDING);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			Notification saved = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.SENT);
			saved.setSentAt(LocalDateTime.now());

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(notificationRepository.save(captor.capture())).thenReturn(saved);

			NotificationPatchRequest patch = new NotificationPatchRequest();
			patch.setStatus(NotificationStatus.SENT);

			notificationService.updateNotification(1L, patch);

			assertThat(captor.getValue().getSentAt()).isNotNull();
		}
	}

	// =========================================================================
	// getNotifications
	// =========================================================================

	@Nested
	@DisplayName("getNotifications()")
	class GetNotifications {

		@Test
		@DisplayName("Returns paged NotificationResponse list")
		void returnsMappedPage() {
			Notification n = buildNotification(1L, 1L, NotificationType.EMAIL,
					"Sub", "Msg", NotificationStatus.SENT);
			n.setSentAt(LocalDateTime.now());

			Page<Notification> page = new PageImpl<>(List.of(n));
			Pageable pageable = PageRequest.of(0, 10);

			when(notificationRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

			Page<NotificationResponse> result = notificationService.getNotifications(
					1L, null, null, null, NotificationStatus.SENT,
					NotificationType.EMAIL, null, null, pageable);

			assertThat(result.getContent()).hasSize(1);
			assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
			assertThat(result.getContent().get(0).getStatus()).isEqualTo("SENT");
		}

		@Test
		@DisplayName("Empty result → returns empty page")
		void emptyResult_returnsEmptyPage() {
			Page<Notification> emptyPage = new PageImpl<>(List.of());
			Pageable pageable = PageRequest.of(0, 10);

			when(notificationRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

			Page<NotificationResponse> result = notificationService.getNotifications(
					null, null, null, null, null, null, null, null, pageable);

			assertThat(result.getContent()).isEmpty();
		}
	}

	// =========================================================================
	// getById
	// =========================================================================

	@Nested
	@DisplayName("getById()")
	class GetById {

		@Test
		@DisplayName("Existing id → returns mapped response")
		void existingId_returnsResponse() {
			Notification n = buildNotification(5L, 2L, NotificationType.SMS,
					"Sub", "Msg", NotificationStatus.SENT);
			n.setSentAt(LocalDateTime.now());

			when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));

			NotificationResponse response = notificationService.getById(5L);

			assertThat(response.getId()).isEqualTo(5L);
			assertThat(response.getUserId()).isEqualTo(2L);
			assertThat(response.getStatus()).isEqualTo("SENT");
		}

		@Test
		@DisplayName("Non-existent id → ResourceNotFoundException")
		void notFound_throwsException() {
			when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> notificationService.getById(99L))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Notification not found with id: 99");
		}
	}

	// =========================================================================
	// getInAppNotifications
	// =========================================================================

	@Nested
	@DisplayName("getInAppNotifications()")
	class GetInAppNotifications {

		@Test
		@DisplayName("Returns IN_APP notifications for user")
		void returnsInAppList() {
			Notification n1 = buildNotification(1L, 3L, NotificationType.IN_APP,
					"Sub1", "Msg1", NotificationStatus.SENT);
			Notification n2 = buildNotification(2L, 3L, NotificationType.IN_APP,
					"Sub2", "Msg2", NotificationStatus.READ);

			when(notificationRepository.findByUserIdAndNotificationType(3L, NotificationType.IN_APP))
					.thenReturn(List.of(n1, n2));

			List<NotificationResponse> result = notificationService.getInAppNotifications(3L);

			assertThat(result).hasSize(2);
			assertThat(result.get(0).getUserId()).isEqualTo(3L);
			assertThat(result.get(1).getStatus()).isEqualTo("READ");
		}

		@Test
		@DisplayName("No IN_APP notifications → returns empty list")
		void noInApp_returnsEmpty() {
			when(notificationRepository.findByUserIdAndNotificationType(99L, NotificationType.IN_APP))
					.thenReturn(List.of());

			List<NotificationResponse> result = notificationService.getInAppNotifications(99L);

			assertThat(result).isEmpty();
		}
	}

	// =========================================================================
	// markAsRead
	// =========================================================================

	@Nested
	@DisplayName("markAsRead()")
	class MarkAsRead {

		@Test
		@DisplayName("Existing notification → status set to READ and saved")
		void marksAsRead() {
			Notification n = buildNotification(1L, 1L, NotificationType.IN_APP,
					"Sub", "Msg", NotificationStatus.SENT);

			when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

			notificationService.markAsRead(1L);

			assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
			assertThat(n.getUpdatedAt()).isNotNull();
			verify(notificationRepository).save(n);
		}

		@Test
		@DisplayName("Non-existent notification → ResourceNotFoundException")
		void notFound_throwsException() {
			when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> notificationService.markAsRead(99L))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Notification not found: 99");
		}
	}

	// =========================================================================
	// getUnreadCount
	// =========================================================================

	@Nested
	@DisplayName("getUnreadCount()")
	class GetUnreadCount {

		@Test
		@DisplayName("Returns count of unread IN_APP notifications")
		void returnsUnreadCount() {
			when(notificationRepository.countByUserIdAndNotificationTypeAndStatus(
					1L, NotificationType.IN_APP, NotificationStatus.SENT)).thenReturn(5L);

			Long count = notificationService.getUnreadCount(1L);

			assertThat(count).isEqualTo(5L);
		}

		@Test
		@DisplayName("No unread notifications → returns 0")
		void noUnread_returnsZero() {
			when(notificationRepository.countByUserIdAndNotificationTypeAndStatus(
					2L, NotificationType.IN_APP, NotificationStatus.SENT)).thenReturn(0L);

			Long count = notificationService.getUnreadCount(2L);

			assertThat(count).isZero();
		}
	}

	// =========================================================================
	// handleProductEvent
	// =========================================================================

	@Nested
	@DisplayName("handleProductEvent()")
	class HandleProductEvent {

		@Test
		@DisplayName("Sends UserFetchRequestEvent to userFetch-out-0")
		void sendsUserFetchRequest() {
			ProductEvent event = new ProductEvent();
			event.setProductId(1L);
			event.setTitle("Clean Code");
			event.setAuthor("Robert Martin");
			event.setPrice(499.0);

			when(streamBridge.send(anyString(), any())).thenReturn(true);

			notificationService.handleProductEvent(event);

			ArgumentCaptor<UserFetchRequestEvent> captor =
					ArgumentCaptor.forClass(UserFetchRequestEvent.class);
			verify(streamBridge).send(eq("userFetch-out-0"), captor.capture());

			// requestId is randomly generated — just verify it is set (non-null)
			assertThat(captor.getValue().getRequestId()).isNotNull();
		}
	}

	// =========================================================================
	// handleCategoryEvent
	// =========================================================================

	@Nested
	@DisplayName("handleCategoryEvent()")
	class HandleCategoryEvent {

		@Test
		@DisplayName("Sends UserFetchRequestEvent to userFetchForCategory-out-0")
		void sendsUserFetchRequest() {
			CategoryEvent event = new CategoryEvent();
			event.setCategoryId(10L);
			event.setName("Science");

			when(streamBridge.send(anyString(), any())).thenReturn(true);

			notificationService.handleCategoryEvent(event);

			ArgumentCaptor<UserFetchRequestEvent> captor =
					ArgumentCaptor.forClass(UserFetchRequestEvent.class);
			verify(streamBridge).send(eq("userFetchForCategory-out-0"), captor.capture());

			assertThat(captor.getValue().getRequestId()).isNotNull();
		}
	}

	// =========================================================================
	// handleUserEmails
	// =========================================================================

	@Nested
	@DisplayName("handleUserEmails()")
	class HandleUserEmails {

		@Test
		@DisplayName("Matching ProductEvent → saves email notification per user")
		void matchingProductEvent_savesNotification() {
			// First put a ProductEvent in the requestMap via handleProductEvent
			ProductEvent product = new ProductEvent();
			product.setProductId(1L);
			product.setTitle("Effective Java");
			product.setAuthor("Joshua Bloch");
			product.setPrice(599.0);

			when(streamBridge.send(anyString(), any())).thenReturn(true);
			notificationService.handleProductEvent(product);

			// Capture the requestId that was stored
			ArgumentCaptor<UserFetchRequestEvent> captor =
					ArgumentCaptor.forClass(UserFetchRequestEvent.class);
			verify(streamBridge).send(eq("userFetch-out-0"), captor.capture());
			Long requestId = captor.getValue().getRequestId();

			// Now fire handleUserEmails with that same requestId
			UserEmailDto user = buildUser(5L, "user@example.com");
			UserEmailEvent emailEvent = new UserEmailEvent();
			emailEvent.setRequestId(requestId);
			emailEvent.setUsers(List.of(user));

			when(notificationRepository.save(any(Notification.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleUserEmails(emailEvent);

			// One EMAIL notification should be saved for the user
			ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationRepository, atLeastOnce()).save(notifCaptor.capture());

			Notification saved = notifCaptor.getValue();
			assertThat(saved.getNotificationType()).isEqualTo(NotificationType.EMAIL);
			assertThat(saved.getUserId()).isEqualTo(5L);
			assertThat(saved.getSubject()).contains("Effective Java");
		}

		@Test
		@DisplayName("Unknown requestId → does nothing, no save called")
		void unknownRequestId_doesNothing() {
			UserEmailEvent emailEvent = new UserEmailEvent();
			emailEvent.setRequestId(999999L);
			emailEvent.setUsers(List.of(buildUser(1L, "x@x.com")));

			notificationService.handleUserEmails(emailEvent);

			verifyNoInteractions(notificationRepository);
		}
	}

	// =========================================================================
	// handleUserEmails1
	// =========================================================================

	@Nested
	@DisplayName("handleUserEmails1()")
	class HandleUserEmails1 {

		@Test
		@DisplayName("Matching CategoryEvent → saves email notification per user")
		void matchingCategoryEvent_savesNotification() {
			CategoryEvent category = new CategoryEvent();
			category.setCategoryId(2L);
			category.setName("Fiction");

			when(streamBridge.send(anyString(), any())).thenReturn(true);
			notificationService.handleCategoryEvent(category);

			ArgumentCaptor<UserFetchRequestEvent> captor =
					ArgumentCaptor.forClass(UserFetchRequestEvent.class);
			verify(streamBridge).send(eq("userFetchForCategory-out-0"), captor.capture());
			Long requestId = captor.getValue().getRequestId();

			UserEmailDto user = buildUser(7L, "cat@example.com");
			UserEmailEvent emailEvent = new UserEmailEvent();
			emailEvent.setRequestId(requestId);
			emailEvent.setUsers(List.of(user));

			when(notificationRepository.save(any(Notification.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleUserEmails1(emailEvent);

			ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationRepository, atLeastOnce()).save(notifCaptor.capture());

			Notification saved = notifCaptor.getValue();
			assertThat(saved.getNotificationType()).isEqualTo(NotificationType.EMAIL);
			assertThat(saved.getUserId()).isEqualTo(7L);
			assertThat(saved.getSubject()).contains("Fiction");
		}

		@Test
		@DisplayName("Unknown requestId → does nothing")
		void unknownRequestId_doesNothing() {
			UserEmailEvent emailEvent = new UserEmailEvent();
			emailEvent.setRequestId(888888L);
			emailEvent.setUsers(List.of(buildUser(1L, "x@x.com")));

			notificationService.handleUserEmails1(emailEvent);

			verifyNoInteractions(notificationRepository);
		}
	}

	// =========================================================================
	// handlePaymentEvent
	// =========================================================================

	@Nested
	@DisplayName("handlePaymentEvent()")
	class HandlePaymentEvent {

		@Test
		@DisplayName("SUCCESS status → saves payment success email notification")
		void successPayment_savesNotification() {
			PaymentEvent event = new PaymentEvent();
			event.setPaymentId(1L);
			event.setOrderId(100L);
			event.setUserId(2L);
			event.setAmount(999.0);
			event.setStatus("SUCCESS");
			event.setMethod("UPI");
			event.setTransactionId("TXN123");

			when(notificationRepository.save(any(Notification.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			notificationService.handlePaymentEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationRepository).save(captor.capture());

			Notification saved = captor.getValue();
			assertThat(saved.getUserId()).isEqualTo(2L);
			assertThat(saved.getNotificationType()).isEqualTo(NotificationType.EMAIL);
			assertThat(saved.getSubject()).contains("Payment Successful");
			assertThat(saved.getMessage()).contains("TXN123");
		}

		@Test
		@DisplayName("Non-SUCCESS status → saves refund email notification")
		void refundPayment_savesNotification() {
			PaymentEvent event = new PaymentEvent();
			event.setPaymentId(2L);
			event.setOrderId(101L);
			event.setUserId(3L);
			event.setAmount(250.0);
			event.setStatus("REFUNDED");
			event.setTransactionId("TXN456");

			when(notificationRepository.save(any(Notification.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			notificationService.handlePaymentEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationRepository).save(captor.capture());

			Notification saved = captor.getValue();
			assertThat(saved.getSubject()).contains("Payment Refunded");
			assertThat(saved.getMessage()).contains("3-5 business days");
		}
	}

	// =========================================================================
	// handleOrderEvent
	// =========================================================================

	@Nested
	@DisplayName("handleOrderEvent()")
	class HandleOrderEvent {

		@Test
		@DisplayName("CONFIRMED status → saves email, SMS, PUSH, IN_APP notifications")
		void confirmedOrder_savesAllNotifications() {
			OrderEvent event = new OrderEvent();
			event.setOrderId(200L);
			event.setUserId(1L);
			event.setTotalAmount(1500.0);
			event.setStatus("CONFIRMED");
			event.setShippingAddress("123 Main St");

			UserEmailDto user = buildUser(1L, "user@test.com");
			when(userFeignClient.getUser1ById(1L)).thenReturn(user);
			when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleOrderEvent(event);

			// EMAIL + SMS + PUSH + IN_APP = 4 saves
			verify(notificationRepository, times(4)).save(any(Notification.class));
		}

		@Test
		@DisplayName("CANCELLED status → saves email, SMS, PUSH, IN_APP notifications")
		void cancelledOrder_savesAllNotifications() {
			OrderEvent event = new OrderEvent();
			event.setOrderId(201L);
			event.setUserId(2L);
			event.setTotalAmount(800.0);
			event.setStatus("CANCELLED");
			event.setReason("Out of stock");

			UserEmailDto user = buildUser(2L, "user2@test.com");
			when(userFeignClient.getUser1ById(2L)).thenReturn(user);
			when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleOrderEvent(event);

			verify(notificationRepository, times(4)).save(any(Notification.class));
		}

		@Test
		@DisplayName("SHIPPED status → saves email, PUSH, IN_APP notifications")
		void shippedOrder_savesThreeNotifications() {
			OrderEvent event = new OrderEvent();
			event.setOrderId(202L);
			event.setUserId(3L);
			event.setStatus("SHIPPED");

			UserEmailDto user = buildUser(3L, "user3@test.com");
			when(userFeignClient.getUser1ById(3L)).thenReturn(user);
			when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleOrderEvent(event);

			// EMAIL + PUSH + IN_APP = 3 saves
			verify(notificationRepository, times(3)).save(any(Notification.class));
		}

		@Test
		@DisplayName("DELIVERED status → saves email, PUSH, IN_APP notifications")
		void deliveredOrder_savesThreeNotifications() {
			OrderEvent event = new OrderEvent();
			event.setOrderId(203L);
			event.setUserId(4L);
			event.setStatus("DELIVERED");

			UserEmailDto user = buildUser(4L, "user4@test.com");
			when(userFeignClient.getUser1ById(4L)).thenReturn(user);
			when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleOrderEvent(event);

			// EMAIL + PUSH + IN_APP = 3 saves
			verify(notificationRepository, times(3)).save(any(Notification.class));
		}

		@Test
		@DisplayName("CONFIRMED order email contains correct subject and amount")
		void confirmedOrder_emailContent() {
			OrderEvent event = new OrderEvent();
			event.setOrderId(204L);
			event.setUserId(5L);
			event.setTotalAmount(2000.0);
			event.setStatus("CONFIRMED");
			event.setShippingAddress("456 Park Ave");

			UserEmailDto user = buildUser(5L, "buyer@test.com");
			when(userFeignClient.getUser1ById(5L)).thenReturn(user);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			when(notificationRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleOrderEvent(event);

			// Find the EMAIL notification among all saved
			Notification emailNotif = captor.getAllValues().stream()
					.filter(n -> n.getNotificationType() == NotificationType.EMAIL)
					.findFirst().orElseThrow();

			assertThat(emailNotif.getSubject()).contains("Order Confirmed");
			assertThat(emailNotif.getMessage()).contains("2000.0");
		}

		@Test
		@DisplayName("Feign exception → handled gracefully, no exception propagated")
		void feignException_handledGracefully() {
			OrderEvent event = new OrderEvent();
			event.setOrderId(205L);
			event.setUserId(6L);
			event.setStatus("CONFIRMED");

			when(userFeignClient.getUser1ById(6L)).thenThrow(new RuntimeException("Feign error"));

			// Should NOT throw — exception is caught internally
			assertThatNoException().isThrownBy(() -> notificationService.handleOrderEvent(event));
			verifyNoInteractions(notificationRepository);
		}
	}

	// =========================================================================
	// handleLowStockEvent
	// =========================================================================

	@Nested
	@DisplayName("handleLowStockEvent()")
	class HandleLowStockEvent {

		@Test
		@DisplayName("Saves IN_APP low stock notification for admin (userId=1)")
		void savesLowStockNotification() {
			// LowStockEvent fields: productId (Long), availableQuantity (Integer), message (String)
			LowStockEvent event = new LowStockEvent();
			event.setProductId(55L);
			event.setAvailableQuantity(3);
			event.setMessage("Low stock warning");

			when(notificationRepository.save(any(Notification.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleLowStockEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationRepository).save(captor.capture());

			Notification saved = captor.getValue();
			assertThat(saved.getUserId()).isEqualTo(1L);
			assertThat(saved.getNotificationType()).isEqualTo(NotificationType.IN_APP);
			assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
			assertThat(saved.getSubject()).contains("55");
			assertThat(saved.getMessage()).contains("3 units");
		}

		@Test
		@DisplayName("Message contains product ID and available quantity")
		void messageContainsCorrectDetails() {
			LowStockEvent event = new LowStockEvent();
			event.setProductId(77L);
			event.setAvailableQuantity(1);
			event.setMessage("Critical stock");

			when(notificationRepository.save(any(Notification.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			notificationService.handleLowStockEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationRepository).save(captor.capture());

			assertThat(captor.getValue().getMessage()).contains("77").contains("1 units");
		}
	}

	// =========================================================================
	// getUserWithCircuitBreaker
	// =========================================================================

	@Nested
	@DisplayName("getUserWithCircuitBreaker()")
	class GetUserWithCircuitBreaker {

		@Test
		@DisplayName("Feign returns user → returned as-is")
		void feignSuccess_returnsUser() {
			UserEmailDto user = buildUser(1L, "ok@test.com");
			when(userFeignClient.getUser1ById(1L)).thenReturn(user);

			UserEmailDto result = notificationService.getUserWithCircuitBreaker(1L);

			assertThat(result.getUserId()).isEqualTo(1L);
			assertThat(result.getEmail()).isEqualTo("ok@test.com");
		}

		@Test
		@DisplayName("Fallback method returns fallback user with correct userId")
		void fallback_returnsFallbackUser() {
			RuntimeException ex = new RuntimeException("Service down");

			UserEmailDto result = notificationService.userFallback(99L, ex);

			// UserEmailDto fields: userId (Long), email (String)
			assertThat(result.getUserId()).isEqualTo(99L);
			assertThat(result.getEmail()).isEqualTo("fallback-user@example.com");
		}
	}
}