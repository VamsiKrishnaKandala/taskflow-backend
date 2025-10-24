package com.taskflow.userservice.handler;

import com.taskflow.userservice.dto.*;
import com.taskflow.userservice.exception.AccessDeniedException;
import com.taskflow.userservice.exception.ErrorResponse;
import com.taskflow.userservice.exception.InvalidLoginException;
import com.taskflow.userservice.exception.ResourceNotFoundException;
import com.taskflow.userservice.service.UserService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Handles HTTP requests for User operations using functional endpoints.
 * Validates requests, delegates to UserService, and builds responses.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserHandler {

    private final UserService userService;
    private final Validator validator;

    /**
     * Handles POST /users - User registration.
     * @param request The server request containing UserCreateRequest DTO.
     * @return 201 Created with UserResponse, or 400/409 error response.
     */
    public Mono<ServerResponse> handleRegisterUser(ServerRequest request) {
        log.info("Handling POST /users request (Registration)");

        Mono<UserCreateRequest> requestMono = request.bodyToMono(UserCreateRequest.class)
                .doOnNext(this::validateRequest);

        return requestMono
                .flatMap(userService::registerUser)
                .flatMap(userResponse ->
                        ServerResponse.status(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(userResponse)
                )
                // Let GlobalExceptionHandler handle specific exceptions (Duplicate, Validation)
                .onErrorResume(e -> Mono.error(e)); // Re-throw other errors
    }

    /**
     * Handles POST /auth/login - User authentication.
     * @param request The server request containing AuthRequest DTO.
     * @return 200 OK with AuthResponse (JWT), or 400/401 error response.
     */
    public Mono<ServerResponse> handleLoginUser(ServerRequest request) {
        log.info("Handling POST /auth/login request");

        Mono<AuthRequest> requestMono = request.bodyToMono(AuthRequest.class)
                .doOnNext(this::validateRequest);

        return requestMono
                .flatMap(userService::loginUser)
                .flatMap(authResponse ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(authResponse)
                )
                // Let GlobalExceptionHandler handle InvalidLoginException (401) and validation (400)
                .onErrorResume(e -> Mono.error(e));
    }

    /**
     * Handles GET /users/{id} - Fetch user details by ID.
     * @param request The server request containing user ID path variable and requester headers.
     * @return 200 OK with UserResponse, or 403/404 error response.
     */
    public Mono<ServerResponse> handleGetUserById(ServerRequest request) {
        String requestedUserId = request.pathVariable("id");
        String requesterId = request.headers().firstHeader("X-User-Id");
        String requesterRole = request.headers().firstHeader("X-User-Role");
        log.info("Handling GET /users/{} request (Requester ID: {}, Role: {})",
                 requestedUserId, requesterId, requesterRole);

        return userService.getUserById(requestedUserId, requesterId, requesterRole)
                .flatMap(userResponse ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(userResponse)
                )
                // Handle expected errors directly for correct status codes
                .onErrorResume(AccessDeniedException.class, ex -> errorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied"))
                .onErrorResume(ResourceNotFoundException.class, ex -> errorResponse(ex, HttpStatus.NOT_FOUND, "Resource Not Found"));
                // Let GlobalExceptionHandler handle unexpected 500s
    }

    /**
     * Handles GET /users/list/all - List all users (Admin only).
     * @param request The server request containing requester role header.
     * @return 200 OK with List<UserResponse>, or 403 error response.
     */
    public Mono<ServerResponse> handleListUsers(ServerRequest request) {
        String requesterRole = request.headers().firstHeader("X-User-Role");
        log.info("Handling GET /users/list/all request (Requester Role: {})", requesterRole);

        Flux<UserResponse> userFlux = userService.findAllUsers(requesterRole);

        // Handle errors from the Flux stream specifically
        return userFlux
                .collectList()
                .flatMap(userList ->
                    ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(userList)
                )
                .onErrorResume(AccessDeniedException.class, ex -> errorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied"));
                // Let GlobalExceptionHandler handle unexpected 500s
    }

    /**
     * Handles PUT /users/{id}/role - Update a user's role (Admin only).
     * @param request The server request containing user ID, requester headers, and UserRoleUpdateRequest DTO.
     * @return 200 OK with updated UserResponse, or 400/403/404 error response.
     */
    public Mono<ServerResponse> handleUpdateUserRole(ServerRequest request) {
        String userId = request.pathVariable("id");
        String requesterRole = request.headers().firstHeader("X-User-Role"); // For logging
        log.info("Handling PUT /users/{}/role request (Requester Role: {})", userId, requesterRole);

        Mono<UserRoleUpdateRequest> requestMono = request.bodyToMono(UserRoleUpdateRequest.class)
                .doOnNext(this::validateRequest);

        return requestMono
                // Assuming service layer performs role check for this operation too
                .flatMap(updateRequest -> userService.updateUserRole(userId, updateRequest.getNewRole()))
                .flatMap(updatedUser ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(updatedUser)
                )
                // Handle expected errors directly
                .onErrorResume(ServerWebInputException.class, ex -> errorResponse(ex, HttpStatus.BAD_REQUEST, "Validation Failed"))
                .onErrorResume(AccessDeniedException.class, ex -> errorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied")) // If service adds role check
                .onErrorResume(ResourceNotFoundException.class, ex -> errorResponse(ex, HttpStatus.NOT_FOUND, "Resource Not Found"));
                // Let GlobalExceptionHandler handle unexpected 500s
    }

    /**
     * Handles POST /auth/logout - Blacklist the provided JWT.
     * @param request The server request containing the Authorization header.
     * @return 200 OK on success, or 401/500 error response.
     */
    public Mono<ServerResponse> handleLogoutUser(ServerRequest request) {
        log.info("Handling POST /auth/logout request");
        String authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
             log.warn("Logout request missing or invalid Authorization header.");
             // Use InvalidLoginException so GlobalExceptionHandler maps it to 401
             return Mono.error(new InvalidLoginException("Missing or invalid Authorization header for logout"));
        }

        return userService.logoutUser(authHeader)
                .then(ServerResponse.ok().build()) // Return 200 OK empty body
                // Let GlobalExceptionHandler handle potential errors from service (like IllegalArgumentException)
                .onErrorResume(e -> Mono.error(e));
    }

    /**
     * Handles DELETE /users/{id} - Delete a user (Admin only).
     * @param request The server request containing user ID and requester headers.
     * @return 204 No Content on success, or 403/404 error response.
     */
    public Mono<ServerResponse> handleDeleteUser(ServerRequest request) {
        String userIdToDelete = request.pathVariable("id");
        String requesterRole = request.headers().firstHeader("X-User-Role");
        log.info("Handling DELETE /users/{} request (Requester Role: {})", userIdToDelete, requesterRole);

        return userService.deleteUserById(userIdToDelete, requesterRole)
                .then(ServerResponse.noContent().build()) // Return 204 No Content
                // Handle expected errors directly
                .onErrorResume(AccessDeniedException.class, ex -> errorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied"))
                .onErrorResume(ResourceNotFoundException.class, ex -> errorResponse(ex, HttpStatus.NOT_FOUND, "Resource Not Found"));
                // Let GlobalExceptionHandler handle unexpected 500s
    }

    /**
     * Validates DTOs using Jakarta Bean Validation.
     * @param dto The DTO object to validate.
     * @throws ServerWebInputException if validation fails.
     */
    private void validateRequest(Object dto) {
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String errorDetails = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining(", "));
            log.warn("Validation failed for request DTO [{}]: {}", dto.getClass().getSimpleName(), errorDetails);
            // Throw exception handled by GlobalExceptionHandler as 400
            throw new ServerWebInputException(errorDetails);
        }
    }

    /**
     * Helper to build standard error responses within the handler.
     */
    private Mono<ServerResponse> errorResponse(Throwable ex, HttpStatus status, String message) {
         ErrorResponse errorBody = new ErrorResponse(
                            status.value(),
                            message,
                            ex.getMessage() // Use exception message as detail
                     );
         return ServerResponse.status(status)
                           .contentType(MediaType.APPLICATION_JSON)
                           .bodyValue(errorBody);
    }
}