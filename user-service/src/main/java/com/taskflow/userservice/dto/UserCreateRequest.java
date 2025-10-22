package com.taskflow.userservice.dto;

import com.taskflow.userservice.model.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCreateRequest {
	
	@NotEmpty(message = "ID cannot be empty")
	@Size(min=3,max=50, message="ID must be between 3 and 50 characters")
	private String id;
	
	@NotEmpty(message = "Name cannot be empty")
	private String name;
	
	@Email(message = "Email should be valid")
	@NotEmpty(message = "Email cannot be empty")
	private String email;
	
	@NotEmpty(message = "Password cannot be empty")
	@Size(min=8, message="Password must be at least 8 characters long")
	private String password;
	
	@NotNull(message = "Role cannot be null")
	private Role role;
}
