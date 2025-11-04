package com.taskflow.userservice.service;

import com.taskflow.userservice.dto.AuthRequest;
import com.taskflow.userservice.dto.AuthResponse;
import com.taskflow.userservice.dto.UserCreateRequest;
import com.taskflow.userservice.dto.UserResponse;
import com.taskflow.userservice.exception.AccessDeniedException;
import com.taskflow.userservice.exception.DuplicateResourceException;
import com.taskflow.userservice.exception.InvalidLoginException;
import com.taskflow.userservice.exception.ResourceNotFoundException;
import com.taskflow.userservice.model.BlacklistedToken;
import com.taskflow.userservice.model.Role;
import com.taskflow.userservice.model.User;
import com.taskflow.userservice.repository.BlacklistedTokenRepository;
import com.taskflow.userservice.repository.UserRepository;
import com.taskflow.userservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException; // Added for specific catch
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Implementation of the UserService interface.
 * Handles business logic for user management and authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    /**
     * Registers a new user after checking for duplicate ID and email. Hashes the password.
     *
     * @param createRequest DTO containing new user data.
     * @return Mono<UserResponse> containing the created user's data (excluding password),
     * or an error Mono if duplicates are found.
     */
    @Override
    public Mono<UserResponse> registerUser(UserCreateRequest createRequest) {
        log.info("Attempting to register user with email: {}", createRequest.getEmail());

        User user = User.builder()
                .id(createRequest.getId())
                .name(createRequest.getName())
                .email(createRequest.getEmail())
                .password(passwordEncoder.encode(createRequest.getPassword()))
                .role(createRequest.getRole())
                // isNew defaults to true via @Builder.Default
                .build();

        return userRepository.findByEmail(user.getEmail())
                .flatMap(existingUser -> {
                    log.warn("Registration failed: Email already exists {}", user.getEmail());
                    return Mono.<User>error(new DuplicateResourceException("Email already exists: " + user.getEmail()));
                })
                .switchIfEmpty(Mono.defer(() -> userRepository.findById(user.getId())))
                .flatMap(existingUser -> {
                    log.warn("Registration failed: ID already exists {}", user.getId());
                    return Mono.<User>error(new DuplicateResourceException("User ID already exists: " + user.getId()));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Saving new user with ID: {}", user.getId());
                    return userRepository.save(user); // save will perform INSERT due to Persistable.isNew()
                }))
                .map(UserResponse::fromEntity);
    }

    /**
     * Authenticates a user based on email and password, generates a JWT upon success.
     *
     * @param authRequest DTO containing login credentials.
     * @return Mono<AuthResponse> containing the JWT and basic user info,
     * or an error Mono if credentials are invalid or user not found.
     */
    @Override
    public Mono<AuthResponse> loginUser(AuthRequest authRequest) {
        log.info("Attempting login for user: {}", authRequest.getEmail());

        return userRepository.findByEmail(authRequest.getEmail())
                .flatMap(user -> {
                    if (passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
                        log.info("Login successful for user: {}", authRequest.getEmail());
                        String token = jwtUtil.generateToken(user);
                        return Mono.just(AuthResponse.builder()
                                .token(token)
                                .userId(user.getId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .role(user.getRole())
                                .build());
                    } else {
                        log.warn("Invalid credentials provided for user: {}", authRequest.getEmail());
                        return Mono.error(new InvalidLoginException("Invalid credentials"));
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Login failed, user not found: {}", authRequest.getEmail());
                    return Mono.error(new InvalidLoginException("Invalid credentials")); // Consistent error message
                }));
    }

    /**
     * Retrieves a user by their ID, performing authorization checks.
     * Allows retrieval only if the requester is an ADMIN or retrieving their own profile.
     *
     * @param requestedUserId The ID of the user profile being requested.
     * @param requesterId     The ID of the user making the request (from token).
     * @param requesterRole   The Role of the user making the request (from token).
     * @return Mono<UserResponse> containing the user's data,
     * or an error Mono if not found or not authorized.
     */
    @Override
    public Mono<UserResponse> getUserById(String requestedUserId, String requesterId, String requesterRole) {
        log.info("Fetching user by ID: {} requested by User ID: {}, Role: {}",
                 requestedUserId, requesterId, requesterRole);

        // --- MODIFIED AUTHORIZATION CHECK (RULE B + Manager) ---
        boolean isAdmin = Role.ROLE_ADMIN.name().equals(requesterRole);
        boolean isManager = Role.ROLE_MANAGER.name().equals(requesterRole); // <-- ADD THIS
        boolean isSelf = requestedUserId.equals(requesterId);

        // Allow if Admin, OR Manager, OR self
        if (!isAdmin && !isManager && !isSelf) { // <-- ADDED !isManager
            log.warn("Access Denied: User {} ({}) attempted to access details for user {}",
                     requesterId, requesterRole, requestedUserId);
            return Mono.error(new AccessDeniedException("Users can only access their own details, or must be ADMIN/MANAGER."));
        }
        // --- END MODIFICATION ---

        log.debug("Authorization check passed for user {} to access user {}", requesterId, requestedUserId);
        return userRepository.findById(requestedUserId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("User not found with ID: {}", requestedUserId);
                    return Mono.error(new ResourceNotFoundException("User not found with ID: " + requestedUserId));
                }))
                .map(UserResponse::fromEntity);
    }

    /**
     * Retrieves all users. Requires ADMIN role for full list, or MANAGER role for EMPLOYEE list only.
     *
     * @param requesterRole The Role of the user making the request.
     * @return Flux<UserResponse> emitting all users (for ADMIN), or only EMPLOYEE users (for MANAGER),
     * or an error Flux if the requester is not authorized.
     */
    @Override
    public Flux<UserResponse> findAllUsers(String requesterRole) {
        log.info("Fetching all users requested by role: {}", requesterRole);

        // 1. ADMIN has full access (returns all users)
        if (Role.ROLE_ADMIN.name().equals(requesterRole)) {
            log.info("Admin access: fetching all users.");
            return userRepository.findAll()
                    .map(UserResponse::fromEntity)
                    .doOnError(ex -> log.error("Error fetching all users", ex));
        }

        // 2. MANAGER has restricted access (returns only EMPLOYEE users)
        else if (Role.ROLE_MANAGER.name().equals(requesterRole)) {
            log.info("Manager access: fetching only EMPLOYEE users.");
            
            // Find all users and filter to include only those with the EMPLOYEE role
            return userRepository.findAll()
                    .filter(user -> Role.ROLE_EMPLOYEE.name().equals(user.getRole()))
                    .map(UserResponse::fromEntity)
                    .doOnError(ex -> log.error("Error fetching EMPLOYEE users for manager", ex));
        } 

        // 3. All other roles are denied
        else {
            log.warn("Access Denied: User with role {} attempted to list all users.", requesterRole);
            return Flux.error(new AccessDeniedException("You are not authorized to list users."));
        }
    }

    /**
     * Updates the role of an existing user. (Currently assumes only ADMINs can do this via gateway checks).
     *
     * @param userId  The ID of the user to update.
     * @param newRole The new role to assign.
     * @return Mono<UserResponse> emitting the updated user's data,
     * or an error Mono if the user is not found.
     */
    @Override
    public Mono<UserResponse> updateUserRole(String userId, Role newRole) {
        // Note: Authorization (e.g., only Admin) should ideally be checked here too,
        // using the requesterRole if passed down, or handled via endpoint security.
        log.info("Attempting to update role for user ID: {} to {}", userId, newRole);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("User not found with ID: {} during role update", userId);
                    return Mono.error(new ResourceNotFoundException("User not found with ID: " + userId));
                }))
                .flatMap(user -> {
                    log.info("Found user {}, updating role from {} to {}", userId, user.getRole(), newRole);
                    user.setRole(newRole);
                    user.setNew(false); // Mark as existing for UPDATE
                    return userRepository.save(user);
                })
                .map(UserResponse::fromEntity)
                .doOnError(ex -> log.error("Error updating role for user {}: {}", userId, ex.getMessage()));
    }

    /**
     * Blacklists the provided JWT upon user logout. Ignores attempts to blacklist already blacklisted tokens.
     *
     * @param bearerToken The full "Bearer <token>" string from the Authorization header.
     * @return Mono<Void> indicating completion or error if token format is invalid.
     */
    @Override
    public Mono<Void> logoutUser(String bearerToken) {
        log.info("Attempting to blacklist token on logout");

        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            log.warn("Logout failed: Invalid or missing Bearer token format.");
            // Consider returning InvalidLoginException or similar if GlobalExceptionHandler maps it to 401/400
            return Mono.error(new IllegalArgumentException("Invalid token format for logout"));
        }

        String token = bearerToken.substring(7);
        String signature = jwtUtil.extractSignature(token);
        LocalDateTime expiry = jwtUtil.extractExpirationAsLocalDateTime(token);

        if (signature == null || expiry == null) {
            log.warn("Logout failed: Could not extract signature or expiry from token.");
            return Mono.error(new IllegalArgumentException("Invalid token data for logout"));
        }

        // Don't blacklist already expired tokens
        if (expiry.isBefore(LocalDateTime.now())) {
             log.info("Token already expired, no need to blacklist.");
             return Mono.empty(); // Success, nothing to do
        }

        BlacklistedToken blacklisted = BlacklistedToken.builder()
                .tokenSignature(signature)
                .expiry(expiry)
                // isNew defaults to true via @Builder.Default
                .build();

        log.info("Saving token signature to blacklist with expiry: {}", expiry);
        return blacklistedTokenRepository.save(blacklisted) // save performs INSERT due to Persistable.isNew()
                .doOnError(e -> {
                    // Log differently for duplicate vs other errors
                    if (e instanceof DuplicateKeyException) {
                        log.warn("Attempted to blacklist token that was already blacklisted. Signature: {}", signature);
                    } else {
                        log.error("Failed to save token to blacklist", e); // Log other DB errors
                    }
                })
                .onErrorResume(DuplicateKeyException.class, e -> {
                    // If duplicate, treat as success (idempotent)
                    return Mono.empty(); // Return empty Mono<Void>
                })
                .then(); // Convert Mono<BlacklistedToken> or empty Mono to Mono<Void>
    }

    /**
     * Deletes a user by ID. Requires ADMIN role.
     *
     * @param userIdToDelete The ID of the user to delete.
     * @param requesterRole  The Role of the user making the request.
     * @return Mono<Void> indicating completion,
     * or an error Mono if user not found or requester is not ADMIN.
     */
    @Override
    public Mono<Void> deleteUserById(String userIdToDelete, String requesterRole) {
        log.info("Attempting to delete user ID: {} by user with role: {}", userIdToDelete, requesterRole);

        if (!Role.ROLE_ADMIN.name().equals(requesterRole)) {
            log.warn("Access Denied: User with role {} attempted to delete user {}", requesterRole, userIdToDelete);
            return Mono.error(new AccessDeniedException("Only ADMIN users can delete users."));
        }

        return userRepository.findById(userIdToDelete)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("User not found with ID: {} during deletion attempt", userIdToDelete);
                return Mono.error(new ResourceNotFoundException("User not found with ID: " + userIdToDelete));
            }))
            .flatMap(user -> {
                 log.info("Found user {}, proceeding with deletion by ADMIN", userIdToDelete);
                 return userRepository.deleteById(userIdToDelete); // Returns Mono<Void>
            })
            .doOnError(ex -> log.error("Error deleting user {}: {}", userIdToDelete, ex.getMessage())); // Log unexpected errors
    }
}