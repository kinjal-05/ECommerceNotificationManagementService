package com.notificationservice.specifications;

import com.notificationservice.enums.NotificationStatus;
import com.notificationservice.enums.NotificationType;
import com.notificationservice.models.Notification;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class NotificationSpecification {

	public static Specification<Notification> filter(
			Long userId,
			Long orderId,
			Long paymentId,
			Long shipmentId,
			NotificationStatus status,
			NotificationType type,
			LocalDateTime startDate,
			LocalDateTime endDate
	) {

		return (root, query, cb) -> {

			var predicate = cb.conjunction();

			if (userId != null) {
				predicate = cb.and(predicate, cb.equal(root.get("userId"), userId));
			}

			if (orderId != null) {
				predicate = cb.and(predicate, cb.equal(root.get("orderId"), orderId));
			}

			if (paymentId != null) {
				predicate = cb.and(predicate, cb.equal(root.get("paymentId"), paymentId));
			}

			if (shipmentId != null) {
				predicate = cb.and(predicate, cb.equal(root.get("shipmentId"), shipmentId));
			}

			if (status != null) {
				predicate = cb.and(predicate, cb.equal(root.get("status"), status));
			}

			if (type != null) {
				predicate = cb.and(predicate, cb.equal(root.get("notificationType"), type));
			}

			if (startDate != null) {
				predicate = cb.and(predicate,
						cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
			}

			if (endDate != null) {
				predicate = cb.and(predicate,
						cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
			}

			return predicate;
		};
	}
}