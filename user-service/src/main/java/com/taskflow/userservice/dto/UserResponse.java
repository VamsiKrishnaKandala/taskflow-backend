package com.taskflow.userservice.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.taskflow.userservice.model.Role;
import com.taskflow.userservice.model.User;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
	/**
	 * DTO for securely sending user data to the client.
	 * Importantly, it omits the password.
	 */
	private String id;
	private String name;
	private String email;
	private Role role;
	private List<String> activeProjectIds;
	
	/**
     * A static factory method to map a User entity to a UserResponse DTO.
     * @param user The User entity from the database.
     * @return A UserResponse DTO.
     */
	public static UserResponse fromEntity(User user) {
		List<String> projectIds;
		if(user.getActiveProjectIds()!=null && !user.getActiveProjectIds().isEmpty()) {
			projectIds = Arrays.asList(user.getActiveProjectIds().split(","));
		}else {
			projectIds = Collections.emptyList();
		}
		return UserResponse.builder()
				.id(user.getId())
				.name(user.getName())
				.email(user.getEmail())
				.role(user.getRole())
				.activeProjectIds(projectIds)
				.build();
	}
}
