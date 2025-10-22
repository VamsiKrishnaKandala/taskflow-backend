package com.taskflowpro.projectservice.exception;

/**
 * Thrown when a requested Project is not found in the database.
 */
public class ProjectNotFoundException extends RuntimeException {
	public ProjectNotFoundException(String id) {
		super("Project with ID " + id + "not found.");
	}

}
