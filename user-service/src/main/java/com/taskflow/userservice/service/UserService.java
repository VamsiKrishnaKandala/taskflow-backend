package com.taskflow.userservice.service;

import com.taskflow.userservice.dto.AuthRequest;
import com.taskflow.userservice.dto.AuthResponse;
import com.taskflow.userservice.dto.UserCreateRequest;
import com.taskflow.userservice.dto.UserResponse;
import com.taskflow.userservice.model.Role;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface defining the business logic for User operations.
 * All operations are reactive, returning Monos.
 */
public interface UserService {

    /**
     * Registers a new user in the system.
     *
     * @param createRequest DTO containing new user data (ID, name, email, password, role).
     * @return A Mono emitting the newly created User's data (as UserResponse),
     * or an error Mono if the ID or email already exists.
     */
    Mono<UserResponse> registerUser(UserCreateRequest createRequest);

    /**
     * Authenticates a user and generates a JWT.
     *
     * @param authRequest DTO containing user's email and password.
     * @return A Mono emitting an AuthResponse (containing the JWT and user info),
     * or an error Mono if credentials are invalid.
     */
    Mono<AuthResponse> loginUser(AuthRequest authRequest);

    /**
     * Retrieves a user by their custom ID.
     *
     * @param id The custom ID of the user to find.
     * @return A Mono emitting the User's data (as UserResponse),
     * or an error Mono if the user is not found.
     */
    Mono<UserResponse> getUserById(String requestedUserId, String requesterId, String requesterRole);
    
    Mono<Void> logoutUser(String token);
    
    /**
     * Retrieves all users in the system.
     *
     * @return A Flux emitting all User data (as UserResponse DTOs).
     */
    Flux<UserResponse> findAllUsers(String requesterRole);
    
    /**
     * Updates the role of an existing user.
     *
     * @param userId The ID of the user to update.
     * @param newRole The new role to assign.
     * @return A Mono emitting the updated User's data (as UserResponse),
     * or an error Mono if the user is not found.
     */
    Mono<UserResponse> updateUserRole(String userId, Role newRole);
    
    Mono<Void> deleteUserById(String userIdToDelete, String requesterRole);
}