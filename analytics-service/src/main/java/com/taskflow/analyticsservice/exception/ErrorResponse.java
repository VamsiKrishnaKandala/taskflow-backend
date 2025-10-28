package com.taskflow.analyticsservice.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a standardized error response.
 *
 * @param statusCode The HTTP status code (e.g., 400, 403, 404).
 * @param message A general error title (e.g., "Access Denied").
 * @param details A list of specific error details.
 * @param timestamp The time the error occurred.
 */
public record ErrorResponse(
    int statusCode,
    String message,
    List<String> details,
    LocalDateTime timestamp
) {
    // Constructor for a single detail message
    public ErrorResponse(int statusCode, String message, String detail) {
        this(statusCode, message, List.of(detail), LocalDateTime.now());
    }

    // Constructor for multiple details
    public ErrorResponse(int statusCode, String message, List<String> details) {
        this(statusCode, message, details, LocalDateTime.now());
    }
}