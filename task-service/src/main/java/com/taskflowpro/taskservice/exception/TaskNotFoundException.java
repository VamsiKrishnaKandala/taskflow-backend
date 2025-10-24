package com.taskflowpro.taskservice.exception;

/**
 * Thrown when a Task is not found.
 */
public class TaskNotFoundException extends RuntimeException {
	public TaskNotFoundException(String id) {
		super("Task with ID " + id + " not found.");
	}

}
