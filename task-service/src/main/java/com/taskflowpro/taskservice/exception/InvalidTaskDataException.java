package com.taskflowpro.taskservice.exception;

public class InvalidTaskDataException extends RuntimeException {
    public InvalidTaskDataException(String message) {
        super(message);
    }
}
