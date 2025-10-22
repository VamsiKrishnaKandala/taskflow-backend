package com.taskflow.userservice.exception;

import java.util.List;
import java.time.LocalDateTime;
/**
 * Represents a structured error response sent to the client.
 * @param statusCode The HTTP status code.
 * @param message A user-friendly error message.
 * @param details A list of specific error details (e.g., validation failures).
 * @param timestamp The time the error occurred.
 */

public record ErrorResponse(
	int statusCode,
	String message,
	List<String> details,
	LocalDateTime timestamp
) {
	public ErrorResponse(int statusCode, String message, String detail) {
		this(statusCode, message, List.of(detail), LocalDateTime.now());
	}
	
	public ErrorResponse(int statusCode, String message, List<String> details) {
		this(statusCode, message, details, LocalDateTime.now());
	}
}
