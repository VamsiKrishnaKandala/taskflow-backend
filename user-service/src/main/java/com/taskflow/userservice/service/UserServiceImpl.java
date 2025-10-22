package com.taskflow.userservice.service;

import com.taskflow.userservice.dto.AuthRequest;
import com.taskflow.userservice.dto.AuthResponse;
import com.taskflow.userservice.dto.UserCreateRequest;
import com.taskflow.userservice.dto.UserResponse;
import com.taskflow.userservice.exception.DuplicateResourceException;
import com.taskflow.userservice.exception.ResourceNotFoundException;
import com.taskflow.userservice.model.BlacklistedToken;
import com.taskflow.userservice.model.Role;
import com.taskflow.userservice.model.User;
import com.taskflow.userservice.repository.BlacklistedTokenRepository;
import com.taskflow.userservice.repository.UserRepository;
import com.taskflow.userservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//ADD THIS LINE
import com.taskflow.userservice.exception.InvalidLoginException;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementation of the UserService interface.
 * Handles all business logic for user management and authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    // Dependencies injected via the constructor (thanks to @RequiredArgsConstructor)
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    
    /**
     * Registers a new user.
     * 1. Checks for duplicate email.
     * 2. Checks for duplicate custom ID.
     * 3. Hashes the password.
     * 4. Saves the new user.
     * 5. Returns a DTO.
     *
     * @param createRequest DTO with new user data.
     * @return Mono<UserResponse> or error.
     */
    @Override
    public Mono<UserResponse> registerUser(UserCreateRequest createRequest) {
        log.info("Attempting to register user with email: {}", createRequest.getEmail());
        
        // 1. Convert DTO to User entity
        User user = User.builder()
                .id(createRequest.getId())
                .name(createRequest.getName())
                .email(createRequest.getEmail())
                .password(passwordEncoder.encode(createRequest.getPassword())) // 2. Hash password
                .role(createRequest.getRole())
                .build();

        // 3. Start the reactive chain to check for duplicates
        return userRepository.findByEmail(user.getEmail())
                .flatMap(existingUser -> {
                    // 4. If email exists, throw error
                    log.warn("Registration failed: Email already exists {}", user.getEmail());
                    return Mono.<User>error(new DuplicateResourceException("Email already exists: " + user.getEmail()));
                })
                .switchIfEmpty(Mono.defer(() -> 
                    // 5. If email is unique, check for duplicate ID
                    userRepository.findById(user.getId())
                ))
                .flatMap(existingUser -> {
                    // 6. If ID exists, throw error
                    log.warn("Registration failed: ID already exists {}", user.getId());
                    return Mono.<User>error(new DuplicateResourceException("User ID already exists: " + user.getId()));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 7. If both are unique, save the user
                    log.info("Saving new user with ID: {}", user.getId());
                    return userRepository.save(user);
                }))
                .map(UserResponse::fromEntity); // 8. Convert saved User to UserResponse DTO
    }

    /**
     * Authenticates a user.
     * 1. Finds user by email.
     * 2. Verifies password.
     * 3. Generates JWT.
     * 4. Returns AuthResponse DTO.
     *
     * @param authRequest DTO with login credentials.
     * @return Mono<AuthResponse> or error.
     */
    @Override
    public Mono<AuthResponse> loginUser(AuthRequest authRequest) {
        log.info("Attempting login for user: {}", authRequest.getEmail());
        
        // 1. Find user by email
        return userRepository.findByEmail(authRequest.getEmail())
                .flatMap(user -> {
                    // 2. Check if password matches
                    if (passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
                        log.info("Login successful for user: {}", authRequest.getEmail());
                        // 3. Generate JWT
                        String token = jwtUtil.generateToken(user);
                        // 4. Build response DTO
                        return Mono.just(AuthResponse.builder()
                                .token(token)
                                .userId(user.getId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .role(user.getRole())
                                .build());
                    } else {
                        // 5. Password mismatch
                        log.warn("Invalid credentials for user: {}", authRequest.getEmail());
                        return Mono.error(new InvalidLoginException("Invalid credentials"));
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 6. User not found
                    log.warn("Login failed, user not found: {}", authRequest.getEmail());
                    return Mono.error(new InvalidLoginException("Invalid credentials"));
                }));
    }

    /**
     * Retrieves a user by their custom ID.
     *
     * @param id The custom ID of the user.
     * @return Mono<UserResponse> or error.
     */
    @Override
    public Mono<UserResponse> getUserById(String id) {
        log.info("Fetching user by ID: {}", id);
        
        // 1. Find user by ID
        return userRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. If not found, throw error
                    log.warn("User not found with ID: {}", id);
                    return Mono.error(new ResourceNotFoundException("User not found with ID: " + id));
                }))
                .map(UserResponse::fromEntity); // 3. Convert to DTO
    }
    /**
     * Blacklists the provided JWT.
     * Extracts signature and expiry, then saves to the blacklist table.
     *
     * @param bearerToken The full "Bearer <token>" string from the Authorization header.
     * @return Mono<Void> indicating completion or error.
     */
    @Override
    public Mono<Void> logoutUser(String bearerToken){
    	log.info("Attempting to blacklist token");
    	if(bearerToken == null || !bearerToken.startsWith("Bearer ")) {
    		log.warn("Logout failed: Invalid Bearer token format.");
    		return Mono.error(new IllegalArgumentException("Invalid token format"));
    	}
    	String token = bearerToken.substring(7);
    	String signature = jwtUtil.extractSignature(token);
    	LocalDateTime expiry = jwtUtil.extractExpirationAsLocalDateTime(token);
    	
    	if (signature == null || expiry == null) {
            log.warn("Logout failed: Could not extract signature or expiry from token.");
            return Mono.error(new IllegalArgumentException("Invalid token data"));
        }
    	if(expiry.isBefore(LocalDateTime.now())) {
    		log.info("Token already expired, no need to blacklist.");
    		return Mono.empty();
    	}
    	
    	BlacklistedToken blacklisted = BlacklistedToken.builder()
    			.tokenSignature(signature)
    			.expiry(expiry)
    			.build();
    	log.info("Saving token signature to blacklist with expiry: {}",expiry);
    	return blacklistedTokenRepository.save(blacklisted)
    			.doOnError(e -> log.error("Failed to save token to blacklist",e))
    			.then();
    }
    /**
     * Retrieves all users.
     *
     * @return Flux<UserResponse> emitting all users.
     */
    @Override
    public Flux<UserResponse> findAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll() // Use the repository's findAll method
                .map(UserResponse::fromEntity) // Convert each User entity to UserResponse
                .doOnError(ex -> log.error("Error fetching all users", ex));
    }
    /**
     * Updates the role for a given user ID.
     *
     * @param userId The ID of the user.
     * @param newRole The new role.
     * @return Mono<UserResponse> emitting the updated user or error.
     */
    @Override
    public Mono<UserResponse> updateUserRole(String userId, Role newRole) {
        log.info("Attempting to update role for user ID: {} to {}", userId, newRole);

        // 1. Find the user by ID
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. If user not found, throw error
                    log.warn("User not found with ID: {} during role update", userId);
                    return Mono.error(new ResourceNotFoundException("User not found with ID: " + userId));
                }))
                .flatMap(user -> {
                    // 3. Update the user's role
                    log.debug("Found user {}, updating role from {} to {}", userId, user.getRole(), newRole);
                    user.setRole(newRole);
                    user.setNew(false); // Mark as existing for the save operation

                    // 4. Save the updated user
                    return userRepository.save(user);
                })
                .map(UserResponse::fromEntity) // 5. Convert saved user to DTO
                .doOnError(ex -> log.error("Error updating role for user {}: {}", userId, ex.getMessage()));
    }
}