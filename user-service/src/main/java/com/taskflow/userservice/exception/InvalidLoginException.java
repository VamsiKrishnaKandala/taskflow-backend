package com.taskflow.userservice.exception;

/**
 * Thrown when a user attempts to log in with incorrect credentials
 * (e.g., wrong password or user not found).
 */
public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException(String message) {
        super(message);
    }
}