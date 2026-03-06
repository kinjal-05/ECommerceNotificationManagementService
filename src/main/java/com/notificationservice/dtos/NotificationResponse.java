package com.notificationservice.dtos;

import java.time.LocalDateTime;

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
public class NotificationResponse {
	private Long id;
	private Long userId;
	private String message;
	private String subject;
	private String status;
	private LocalDateTime sentAt;

}

