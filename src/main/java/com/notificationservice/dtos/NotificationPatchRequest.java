package com.notificationservice.dtos;

import com.notificationservice.enums.NotificationStatus;
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
public class NotificationPatchRequest {

	private NotificationStatus status;

	@Size(max = 2000, message = "Message cannot exceed 2000 characters")
	private String message;

	@Size(max = 255, message = "Subject cannot exceed 255 characters")
	private String subject;

}

