package com.notificationservice.consumer;
import java.util.function.Consumer;

import com.notificationservice.commondtos.*;
import com.notificationservice.services.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class KafkaConsumerConfig {

	@Bean
	public Consumer<ProductEvent> productConsumer(NotificationService service) {

		return event -> {

			service.handleProductEvent(event);
		};
	}

	@Bean
	public Consumer<CategoryEvent> categoryConsumer(NotificationService service) {

		return event -> {

			service.handleCategoryEvent(event);
		};
	}

	@Bean
	public Consumer<UserEmailEvent> userEmailsConsumer(NotificationService service) {
		return event -> {

			service.handleUserEmails(event);
		};
	}

	@Bean
	public Consumer<PaymentEvent> paymentConsumer(NotificationService service) {

		return event -> {

			service.handlePaymentEvent(event);
		};
	}

	@Bean
	public Consumer<OrderEvent> orderConsumer(NotificationService service) {

		return event -> {

			service.handleOrderEvent(event);
		};
	}

	@Bean
	public Consumer<LowStockEvent> lowStockConsumer(NotificationService service) {
		return event -> {

			service.handleLowStockEvent(event);
		};
	}

}
