package com.taskflowpro.taskservice.exception;

public class TaskEventPublishingException extends RuntimeException {
    public TaskEventPublishingException(String message) {
        super(message);
    }

    public TaskEventPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}