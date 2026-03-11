package com.notificationservice.models;

import java.time.LocalDateTime;
import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications", indexes = { @Index(name = "idx_user_id", columnList = "userId"),
		@Index(name = "idx_status", columnList = "status"),
		@Index(name = "idx_type", columnList = "notificationType") })
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull(message = "User ID is required")
	@Column(nullable = false)
	private Long userId;

	@NotNull(message = "Notification type is required")
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType notificationType;

	@Column
	private Long orderId;

	@Column
	private Long paymentId;

	@Column
	private Long shipmentId;

	@Column(length = 255)
	private String subject;

	@NotBlank(message = "Message content is required")
	@Column(nullable = false, columnDefinition = "TEXT")
	private String message;

	@NotNull(message = "Status is required")
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationStatus status = NotificationStatus.PENDING;

	private LocalDateTime sentAt;

	@Builder.Default
	@Column(nullable = false)
	private Integer retryCount = 0;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public Notification(Long userId, NotificationType type, String subject, String message) {
		this.userId = userId;
		this.notificationType = type;
		this.subject = subject;
		this.message = message;
	}

	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}