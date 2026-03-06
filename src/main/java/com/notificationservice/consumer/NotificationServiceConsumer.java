package com.notificationservice.consumer;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import com.notificationservice.commondtos.UserDeletedEvent;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import com.notificationservice.models.Notification;
import com.notificationservice.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceConsumer {

	private final NotificationRepository notificationRepository;

	@Bean
	public Consumer<UserDeletedEvent> userDeleted() {
		return event -> {
			log.info("📩 Notification Service received user-deleted for: {}", event.getEmail());

			// Send Email
			notificationRepository.save(Notification.builder().userId(event.getUserId())
					.notificationType(NotificationType.EMAIL).subject("Account Deleted Successfully")
					.message("Hi " + event.getEmail() + ",\n\n" + "Your account has been permanently deleted.\n"
							+ "Deleted at: " + event.getDeletedAt())
					.status(NotificationStatus.SENT).createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now())
					.build());

			// Send SMS
			notificationRepository
					.save(Notification.builder().userId(event.getUserId()).notificationType(NotificationType.SMS)
							.message("Your account has been deleted successfully.").status(NotificationStatus.SENT)
							.createdAt(LocalDateTime.now()).sentAt(LocalDateTime.now()).build());

			log.info("✅ Deletion notifications sent to: {}", event.getEmail());
		};
	}
}