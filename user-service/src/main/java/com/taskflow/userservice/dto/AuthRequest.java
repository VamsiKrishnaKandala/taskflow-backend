package com.taskflow.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AuthRequest {
	@Email
	@NotEmpty
	private String email;
	
	@NotEmpty
	private String password;
}
