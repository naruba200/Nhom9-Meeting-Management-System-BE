package com.example.shopapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
		return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationError(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));

		return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, message));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
		return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, "Thiếu header bắt buộc: " + ex.getHeaderName()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(errorBody(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"));
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
		return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, safeMessage(ex.getMessage())));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống"));
	}

	private Map<String, Object> errorBody(HttpStatus status, String message) {
		return Map.of(
				"timestamp", LocalDateTime.now(),
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", safeMessage(message));
	}

	private String safeMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Yeu cau khong hop le";
		}
		return message;
	}
}