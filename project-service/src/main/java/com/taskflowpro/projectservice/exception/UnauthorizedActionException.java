package com.taskflowpro.projectservice.exception;

/**
 * Exception thrown when a user attempts an action they are not authorized to perform.
 */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}