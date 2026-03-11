package com.notificationservice.exceptions;

import com.notificationservice.dtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
		return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Object> handleBadRequest(BadRequestException ex) {
		return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleGeneric(Exception ex) {
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
	}

	private ResponseEntity<Object> buildResponse(HttpStatus status, String message) {
		return new ResponseEntity<>(new ErrorResponse(status.value(), message, LocalDateTime.now()), status);
	}
}