package com.notificationservice.commondtos;

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
public class PaymentEvent {
	private Long paymentId;
	private Long orderId;
	private Long userId;
	private Double amount;
	private String status;
	private String method;
	private String transactionId;

}

