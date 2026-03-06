package com.notificationservice.commondtos;

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
public class ProductEvent {
	private Long productId;
	private String title;
	private String description;
	private String author;
	private String isbn;
	private double price;
	private String categoryName;
	private LocalDateTime createdAt;

}

