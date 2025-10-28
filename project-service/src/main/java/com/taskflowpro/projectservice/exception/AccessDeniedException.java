package com.taskflowpro.projectservice.exception;

/**
 * Thrown when a user attempts an action they are not authorized to perform.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}