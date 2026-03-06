package com.notificationservice.dtos;

import com.notificationservice.enums.NotificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class NotificationRequest {
	@NotNull(message = "User ID is required")
	private Long userId;

	private Long orderId;
	private Long paymentId;
	private Long shipmentId;

	@Size(max = 255)
	private String subject;

	@Size(max = 2000)
	private String message;

	@NotNull(message = "Notification type is required")
	private NotificationType notificationType;

}

