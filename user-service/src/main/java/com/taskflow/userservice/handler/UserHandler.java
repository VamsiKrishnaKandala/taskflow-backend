package com.taskflow.userservice.handler;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.taskflow.userservice.dto.AuthRequest;
import com.taskflow.userservice.dto.AuthResponse;
import com.taskflow.userservice.dto.UserCreateRequest;
import com.taskflow.userservice.dto.UserResponse;
import com.taskflow.userservice.service.UserService;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handles incoming HTTP requests for User operations.
 * This class is the equivalent of a @RestController in the
 * functional programming model.
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class UserHandler {
	
	private final UserService userService;
	private final Validator validator;
	
	/**
     * Handles the user registration request (POST /users).
     *
     * @param request The incoming ServerRequest, containing the UserCreateRequest in its body.
     * @return A Mono<ServerResponse> with status 201 (Created) and the new user's data,
     * or a 400 (Bad Request) if validation fails, or another error status.
     */
	public Mono<ServerResponse> handleRegisterUser(ServerRequest request){
		log.info("Handling request for user registration");
		
		//extract and validate the request body
		Mono<UserCreateRequest> requestMono = request.bodyToMono(UserCreateRequest.class)
				.doOnNext(this::validateRequest);
		
		//Pass the validated request to the service
		return requestMono
				.flatMap(userService::registerUser)
				.flatMap(UserResponse->
					// On success, return 201 created
					ServerResponse.status(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(UserResponse)
				)
				.doOnError(ex -> log.warn("Registration failed: {}",ex.getMessage()));
	}
	/**
     * Handles the user login request (POST /auth/login).
     *
     * @param request The incoming ServerRequest, containing the AuthRequest in its body.
     * @return A Mono<ServerResponse> with status 200 (OK) and the AuthResponse (JWT),
     * or a 400/401 error.
     */
	public Mono<ServerResponse> handleLoginUser(ServerRequest request){
		log.info("Handling request for user login");
		
		//extract and validate the request body
		Mono<AuthRequest> requestMono = request.bodyToMono(AuthRequest.class)
				.doOnNext(this::validateRequest);
		
		//Pass to service
		return requestMono
				.flatMap(userService::loginUser)
				.flatMap(authResponse -> 
					ServerResponse.ok()
							.contentType(MediaType.APPLICATION_JSON)
							.bodyValue(authResponse)
				)
				.doOnError(ex -> log.warn("Login failed: {}",ex.getMessage()));
	}
	/**
     * Handles the get user by ID request (GET /users/{id}).
     *
     * @param request The incoming ServerRequest, containing the 'id' path variable.
     * @return A Mono<ServerResponse> with status 200 (OK) and the UserResponse,
     * or a 404 (Not Found) if the user doesn't exist.
     */
	public Mono<ServerResponse> handleGetUserById(ServerRequest request){
		
		String userId = request.pathVariable("id");
		log.info("Handling request to get user by ID: {}",userId);
		
		return userService.getUserById(userId)
				.flatMap(userResponse ->
					ServerResponse.ok()
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(userResponse)
				)
				.doOnError(ex->log.warn("Failed to get user by ID {}: {}",userId,ex.getMessage()));
	}
	/**
     * Helper method to manually trigger validation.
     *
     * @param dto The DTO (Data Transfer Object) to validate.
     * @throws org.springframework.web.server.ServerWebInputException if validation fails.
     */
    private void validateRequest(Object dto) {
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            // Collect all validation messages
            String errorDetails = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining(", "));

            log.warn("Validation failed for request: {}", errorDetails);
            
            // Throw the correct exception for bad web input
            throw new org.springframework.web.server.ServerWebInputException(errorDetails);
        }
    }
}
