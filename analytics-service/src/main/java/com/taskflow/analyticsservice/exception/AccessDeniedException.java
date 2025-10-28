package com.taskflow.analyticsservice.exception;

/**
 * Thrown when a user attempts an action they are not authorized to perform
 * (e.g., an Employee trying to access manager-level analytics).
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}