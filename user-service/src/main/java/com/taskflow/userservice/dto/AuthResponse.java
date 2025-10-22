package com.taskflow.userservice.dto;

import com.taskflow.userservice.model.Role;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
	/**
	 * DTO sent back to the client upon successful authentication.
	 * Contains the JWT and basic user info.
	 */
	private String token;
	private String userId;
	private String name;
	private String email;
	private Role role;
}
