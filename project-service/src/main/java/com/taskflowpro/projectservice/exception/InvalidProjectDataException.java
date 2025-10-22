package com.taskflowpro.projectservice.exception;

/**
 * Thrown when invalid or incomplete project data is provided in a request.
 */
public class InvalidProjectDataException extends RuntimeException {
	public InvalidProjectDataException(String message) {
		super(message);
	}

}
