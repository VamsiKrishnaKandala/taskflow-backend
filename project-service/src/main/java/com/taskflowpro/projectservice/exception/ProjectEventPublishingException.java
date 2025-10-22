package com.taskflowpro.projectservice.exception;

/**
 * Exception thrown when a reactive project event fails to publish.
 */
public class ProjectEventPublishingException extends RuntimeException {
    public ProjectEventPublishingException(String message) {
        super(message);
    }

    public ProjectEventPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}