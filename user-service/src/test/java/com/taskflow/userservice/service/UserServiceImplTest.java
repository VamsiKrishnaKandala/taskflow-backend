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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException; // Import for logout test
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl using Mockito and StepVerifier.
 */
@ExtendWith(MockitoExtension.class) // Enables Mockito annotations
class UserServiceImplTest {

    // Mocks for dependencies
    @Mock
    private UserRepository userRepository;
    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    // Instance of the class under test with mocks injected
    @InjectMocks
    private UserServiceImpl userService;

    // Test data reused across tests
    private User adminUser;
    private User employeeUser;
    private UserCreateRequest adminCreateRequest;
    private UserCreateRequest employeeCreateRequest;
    private AuthRequest adminAuthRequest;
    private AuthRequest employeeAuthRequest;

    @BeforeEach
    void setUp() {
        // Initialize common test data before each test
        adminUser = User.builder()
                .id("admin-001")
                .name("Admin User")
                .email("admin@company.com")
                .password("hashedPasswordAdmin")
                .role(Role.ROLE_ADMIN)
                .activeProjectIds("proj-A")
                .isNew(false) // Assume existing for most tests
                .build();

        employeeUser = User.builder()
                .id("emp-101")
                .name("Employee User")
                .email("employee@company.com")
                .password("hashedPasswordEmp")
                .role(Role.ROLE_EMPLOYEE)
                .activeProjectIds("proj-B")
                .isNew(false)
                .build();

        adminCreateRequest = UserCreateRequest.builder()
                .id(adminUser.getId())
                .name(adminUser.getName())
                .email(adminUser.getEmail())
                .password("rawPasswordAdmin")
                .role(adminUser.getRole())
                .build();

        employeeCreateRequest = UserCreateRequest.builder()
                .id(employeeUser.getId())
                .name(employeeUser.getName())
                .email(employeeUser.getEmail())
                .password("rawPasswordEmp")
                .role(employeeUser.getRole())
                .build();

        adminAuthRequest = new AuthRequest();
        adminAuthRequest.setEmail(adminUser.getEmail());
        adminAuthRequest.setPassword("rawPasswordAdmin");

        employeeAuthRequest = new AuthRequest();
        employeeAuthRequest.setEmail(employeeUser.getEmail());
        employeeAuthRequest.setPassword("rawPasswordEmp");
    }

    // --- Nested Test Classes for better organization ---

    @Nested
    @DisplayName("registerUser Tests")
    class RegisterUserTests {

        @Test
        @DisplayName("Should register user successfully when ID and email are unique")
        void registerUser_Success() {
            // Arrange
            when(userRepository.findByEmail(adminCreateRequest.getEmail())).thenReturn(Mono.empty());
            when(userRepository.findById(adminCreateRequest.getId())).thenReturn(Mono.empty());
            when(passwordEncoder.encode(adminCreateRequest.getPassword())).thenReturn(adminUser.getPassword());
            // Mock save to return the user (as if saved)
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(adminUser));

            // Act
            Mono<UserResponse> result = userService.registerUser(adminCreateRequest);

            // Assert
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getId().equals(adminUser.getId())
                            && response.getEmail().equals(adminUser.getEmail()))
                    .verifyComplete();

            verify(userRepository).findByEmail(adminCreateRequest.getEmail());
            verify(userRepository).findById(adminCreateRequest.getId());
            verify(passwordEncoder).encode(adminCreateRequest.getPassword());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should fail registration with DuplicateResourceException if email exists")
        void registerUser_Fail_DuplicateEmail() {
            // Arrange
            when(userRepository.findByEmail(adminCreateRequest.getEmail())).thenReturn(Mono.just(adminUser)); // Simulate email found

            // Act
            Mono<UserResponse> result = userService.registerUser(adminCreateRequest);

            // Assert
            StepVerifier.create(result)
                    .expectError(DuplicateResourceException.class)
                    .verify();

            verify(userRepository).findByEmail(adminCreateRequest.getEmail());
            verify(userRepository, never()).findById(anyString()); // Should not check ID if email fails
            verify(userRepository, never()).save(any(User.class)); // Should not save
        }

        @Test
        @DisplayName("Should fail registration with DuplicateResourceException if ID exists")
        void registerUser_Fail_DuplicateId() {
            // Arrange
            when(userRepository.findByEmail(adminCreateRequest.getEmail())).thenReturn(Mono.empty()); // Email unique
            when(userRepository.findById(adminCreateRequest.getId())).thenReturn(Mono.just(adminUser)); // ID found
            when(passwordEncoder.encode(adminCreateRequest.getPassword())).thenReturn(adminUser.getPassword());


            // Act
            Mono<UserResponse> result = userService.registerUser(adminCreateRequest);

            // Assert
            StepVerifier.create(result)
                    .expectError(DuplicateResourceException.class)
                    .verify();

            verify(userRepository).findByEmail(adminCreateRequest.getEmail());
            verify(userRepository).findById(adminCreateRequest.getId());
            verify(userRepository, never()).save(any(User.class)); // Should not save
        }
    }

    @Nested
    @DisplayName("loginUser Tests")
    class LoginUserTests {

        @Test
        @DisplayName("Should return AuthResponse with token on successful login")
        void loginUser_Success() {
            // Arrange
            when(userRepository.findByEmail(adminAuthRequest.getEmail())).thenReturn(Mono.just(adminUser));
            when(passwordEncoder.matches(adminAuthRequest.getPassword(), adminUser.getPassword())).thenReturn(true);
            when(jwtUtil.generateToken(adminUser)).thenReturn("valid.admin.token");

            // Act
            Mono<AuthResponse> result = userService.loginUser(adminAuthRequest);

            // Assert
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getToken().equals("valid.admin.token")
                            && response.getUserId().equals(adminUser.getId()))
                    .verifyComplete();

            verify(userRepository).findByEmail(adminAuthRequest.getEmail());
            verify(passwordEncoder).matches(adminAuthRequest.getPassword(), adminUser.getPassword());
            verify(jwtUtil).generateToken(adminUser);
        }

        @Test
        @DisplayName("Should fail login with InvalidLoginException if user not found")
        void loginUser_Fail_UserNotFound() {
            // Arrange
            when(userRepository.findByEmail(adminAuthRequest.getEmail())).thenReturn(Mono.empty()); // Simulate user not found

            // Act
            Mono<AuthResponse> result = userService.loginUser(adminAuthRequest);

            // Assert
            StepVerifier.create(result)
                    .expectError(InvalidLoginException.class)
                    .verify();

            verify(userRepository).findByEmail(adminAuthRequest.getEmail());
            verify(passwordEncoder, never()).matches(anyString(), anyString()); // Password check skipped
            verify(jwtUtil, never()).generateToken(any(User.class)); // Token generation skipped
        }

        @Test
        @DisplayName("Should fail login with InvalidLoginException if password mismatch")
        void loginUser_Fail_PasswordMismatch() {
            // Arrange
            when(userRepository.findByEmail(adminAuthRequest.getEmail())).thenReturn(Mono.just(adminUser));
            when(passwordEncoder.matches(adminAuthRequest.getPassword(), adminUser.getPassword())).thenReturn(false); // Simulate mismatch

            // Act
            Mono<AuthResponse> result = userService.loginUser(adminAuthRequest);

            // Assert
            StepVerifier.create(result)
                    .expectError(InvalidLoginException.class)
                    .verify();

            verify(userRepository).findByEmail(adminAuthRequest.getEmail());
            verify(passwordEncoder).matches(adminAuthRequest.getPassword(), adminUser.getPassword());
            verify(jwtUtil, never()).generateToken(any(User.class)); // Token generation skipped
        }
    }

    @Nested
    @DisplayName("getUserById Tests")
    class GetUserByIdTests {

        @Test
        @DisplayName("Admin should get details of any user")
        void getUserById_AdminGetsOther() {
            // Arrange
            String requestedId = employeeUser.getId();
            String requesterId = adminUser.getId();
            String requesterRole = adminUser.getRole().name();
            when(userRepository.findById(requestedId)).thenReturn(Mono.just(employeeUser));

            // Act
            Mono<UserResponse> result = userService.getUserById(requestedId, requesterId, requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getId().equals(requestedId))
                    .verifyComplete();
            verify(userRepository).findById(requestedId);
        }

        @Test
        @DisplayName("User should get their own details")
        void getUserById_UserGetsSelf() {
            // Arrange
            String requestedId = employeeUser.getId();
            String requesterId = employeeUser.getId(); // Requesting self
            String requesterRole = employeeUser.getRole().name();
            when(userRepository.findById(requestedId)).thenReturn(Mono.just(employeeUser));

            // Act
            Mono<UserResponse> result = userService.getUserById(requestedId, requesterId, requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getId().equals(requestedId))
                    .verifyComplete();
            verify(userRepository).findById(requestedId);
        }

        @Test
        @DisplayName("User should fail to get another non-admin user's details (Access Denied)")
        void getUserById_UserFailsGetOther() {
            // Arrange
            String requestedId = adminUser.getId(); // Employee tries to get Admin details
            String requesterId = employeeUser.getId();
            String requesterRole = employeeUser.getRole().name();
            // No need to mock findById as it should fail before

            // Act
            Mono<UserResponse> result = userService.getUserById(requestedId, requesterId, requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectError(AccessDeniedException.class)
                    .verify();
            verify(userRepository, never()).findById(anyString()); // findById should not be called
        }

        @Test
        @DisplayName("Should fail with ResourceNotFoundException if requested user ID doesn't exist")
        void getUserById_Fail_NotFound() {
            // Arrange
            String requestedId = "non-existent-id";
            String requesterId = adminUser.getId(); // Admin requesting
            String requesterRole = adminUser.getRole().name();
            when(userRepository.findById(requestedId)).thenReturn(Mono.empty()); // Simulate not found

            // Act
            Mono<UserResponse> result = userService.getUserById(requestedId, requesterId, requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectError(ResourceNotFoundException.class)
                    .verify();
            verify(userRepository).findById(requestedId);
        }
    }

    @Nested
    @DisplayName("findAllUsers Tests")
    class FindAllUsersTests {

        @Test
        @DisplayName("Admin should get list of all users")
        void findAllUsers_AdminSuccess() {
            // Arrange
            String requesterRole = Role.ROLE_ADMIN.name();
            when(userRepository.findAll()).thenReturn(Flux.just(adminUser, employeeUser));

            // Act
            Flux<UserResponse> result = userService.findAllUsers(requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(2) // Expecting two users in the flux
                    .verifyComplete();
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("Non-admin should fail to list all users (Access Denied)")
        void findAllUsers_NonAdminFail() {
            // Arrange
            String requesterRole = Role.ROLE_EMPLOYEE.name();
            // No need to mock findAll as it should fail before

            // Act
            Flux<UserResponse> result = userService.findAllUsers(requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectError(AccessDeniedException.class)
                    .verify();
            verify(userRepository, never()).findAll(); // findAll should not be called
        }

         @Test
        @DisplayName("Should return empty Flux if no users exist (Admin request)")
        void findAllUsers_AdminSuccess_Empty() {
            // Arrange
            String requesterRole = Role.ROLE_ADMIN.name();
            when(userRepository.findAll()).thenReturn(Flux.empty()); // Simulate empty table

            // Act
            Flux<UserResponse> result = userService.findAllUsers(requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(0) // Expect zero items
                    .verifyComplete();
            verify(userRepository).findAll();
        }
    }

    @Nested
    @DisplayName("updateUserRole Tests")
    class UpdateUserRoleTests {

        @Test
        @DisplayName("Should successfully update user role")
        void updateUserRole_Success() {
            // Arrange
            String userIdToUpdate = employeeUser.getId();
            Role newRole = Role.ROLE_MANAGER;
            User updatedUser = User.builder() // Simulate the state *after* update
                    .id(userIdToUpdate).name(employeeUser.getName()).email(employeeUser.getEmail())
                    .password(employeeUser.getPassword()).role(newRole).activeProjectIds(employeeUser.getActiveProjectIds())
                    .isNew(false).build();

            when(userRepository.findById(userIdToUpdate)).thenReturn(Mono.just(employeeUser)); // Find original user
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser)); // Mock save returning updated user

            // Act
            Mono<UserResponse> result = userService.updateUserRole(userIdToUpdate, newRole);

            // Assert
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getId().equals(userIdToUpdate) && response.getRole().equals(newRole))
                    .verifyComplete();

            verify(userRepository).findById(userIdToUpdate);
            verify(userRepository).save(argThat(user -> !user.isNew() && user.getRole() == newRole)); // Verify save was called with correct state
        }

        @Test
        @DisplayName("Should fail with ResourceNotFoundException if user to update doesn't exist")
        void updateUserRole_Fail_NotFound() {
            // Arrange
            String userIdToUpdate = "non-existent-id";
            Role newRole = Role.ROLE_MANAGER;
            when(userRepository.findById(userIdToUpdate)).thenReturn(Mono.empty()); // Simulate user not found

            // Act
            Mono<UserResponse> result = userService.updateUserRole(userIdToUpdate, newRole);

            // Assert
            StepVerifier.create(result)
                    .expectError(ResourceNotFoundException.class)
                    .verify();

            verify(userRepository).findById(userIdToUpdate);
            verify(userRepository, never()).save(any(User.class)); // Save should not be called
        }
        // Note: Add AccessDenied test here if role check is implemented within updateUserRole itself
    }


    @Nested
    @DisplayName("logoutUser Tests")
    class LogoutUserTests {
        String validToken = "valid.jwt.token";
        String validBearerToken = "Bearer " + validToken;
        String validSignature = "validSignature";
        LocalDateTime futureExpiry = LocalDateTime.now().plusHours(1);
        LocalDateTime pastExpiry = LocalDateTime.now().minusHours(1);

        @Test
        @DisplayName("Should successfully blacklist a valid, non-expired token")
        void logoutUser_Success() {
            // Arrange
            when(jwtUtil.extractSignature(validToken)).thenReturn(validSignature);
            when(jwtUtil.extractExpirationAsLocalDateTime(validToken)).thenReturn(futureExpiry);
            BlacklistedToken savedToken = BlacklistedToken.builder()
                .tokenSignature(validSignature)
                .expiry(futureExpiry)
                .isNew(false) // Simulate the state *after* being saved
                .build();
            // Mock save to return successfully
            when(blacklistedTokenRepository.save(any(BlacklistedToken.class))).thenReturn(Mono.just(savedToken));


            // Act
            Mono<Void> result = userService.logoutUser(validBearerToken);

            // Assert
            StepVerifier.create(result).verifyComplete(); // Expect successful void completion

            verify(jwtUtil).extractSignature(validToken);
            verify(jwtUtil).extractExpirationAsLocalDateTime(validToken);
            verify(blacklistedTokenRepository).save(any(BlacklistedToken.class));
        }

        @Test
        @DisplayName("Should complete successfully without saving if token is already expired")
        void logoutUser_Success_TokenExpired() {
            // Arrange
            when(jwtUtil.extractSignature(validToken)).thenReturn(validSignature);
            when(jwtUtil.extractExpirationAsLocalDateTime(validToken)).thenReturn(pastExpiry); // Token expired

            // Act
            Mono<Void> result = userService.logoutUser(validBearerToken);

            // Assert
            StepVerifier.create(result).verifyComplete(); // Still completes successfully

            verify(jwtUtil).extractSignature(validToken);
            verify(jwtUtil).extractExpirationAsLocalDateTime(validToken);
            verify(blacklistedTokenRepository, never()).save(any(BlacklistedToken.class)); // Save NOT called
        }

        @Test
        @DisplayName("Should complete successfully (idempotent) if token is already blacklisted")
        void logoutUser_Success_AlreadyBlacklisted() {
             // Arrange
            when(jwtUtil.extractSignature(validToken)).thenReturn(validSignature);
            when(jwtUtil.extractExpirationAsLocalDateTime(validToken)).thenReturn(futureExpiry);
            // Mock save to throw DuplicateKeyException
            when(blacklistedTokenRepository.save(any(BlacklistedToken.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("Duplicate key")));

            // Act
            Mono<Void> result = userService.logoutUser(validBearerToken);

            // Assert
            StepVerifier.create(result).verifyComplete(); // Should complete successfully due to onErrorResume

            verify(jwtUtil).extractSignature(validToken);
            verify(jwtUtil).extractExpirationAsLocalDateTime(validToken);
            verify(blacklistedTokenRepository).save(any(BlacklistedToken.class)); // Save IS called
        }

        @Test
        @DisplayName("Should fail with IllegalArgumentException if Bearer prefix is missing")
        void logoutUser_Fail_InvalidFormat() {
            // Arrange
            String invalidBearerToken = "noBearerPrefixToken";

            // Act
            Mono<Void> result = userService.logoutUser(invalidBearerToken);

            // Assert
            StepVerifier.create(result).expectError(IllegalArgumentException.class).verify();

            verifyNoInteractions(jwtUtil, blacklistedTokenRepository); // Nothing else should be called
        }

        // ---------- THIS IS THE MODIFIED TEST ----------
        @Test
        @DisplayName("Should fail with IllegalArgumentException if token data cannot be extracted")
        void logoutUser_Fail_InvalidData() {
            // Arrange
            when(jwtUtil.extractSignature(validToken)).thenReturn(null); // Simulate signature extraction failure
            // We still need to mock extractExpirationAsLocalDateTime because it WILL be called by the code
            when(jwtUtil.extractExpirationAsLocalDateTime(validToken)).thenReturn(null); // Return null or a dummy value

            // Act
            Mono<Void> result = userService.logoutUser(validBearerToken);

            // Assert
            StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();

            // Verify
            verify(jwtUtil).extractSignature(validToken);
            verify(jwtUtil).extractExpirationAsLocalDateTime(validToken); // Verify this method IS called now
            verify(blacklistedTokenRepository, never()).save(any(BlacklistedToken.class)); // Save still skipped
        }
        // ---------- END OF MODIFIED TEST ----------
    }


    @Nested
    @DisplayName("deleteUserById Tests")
    class DeleteUserByIdTests {

        @Test
        @DisplayName("Admin should successfully delete a user")
        void deleteUserById_AdminSuccess() {
            // Arrange
            String userIdToDelete = employeeUser.getId();
            String requesterRole = Role.ROLE_ADMIN.name();
            when(userRepository.findById(userIdToDelete)).thenReturn(Mono.just(employeeUser)); // User found
            when(userRepository.deleteById(userIdToDelete)).thenReturn(Mono.empty()); // Mock successful deletion (Mono<Void>)

            // Act
            Mono<Void> result = userService.deleteUserById(userIdToDelete, requesterRole);

            // Assert
            StepVerifier.create(result).verifyComplete(); // Expect successful void completion

            verify(userRepository).findById(userIdToDelete);
            verify(userRepository).deleteById(userIdToDelete);
        }

        @Test
        @DisplayName("Non-admin should fail to delete a user (Access Denied)")
        void deleteUserById_NonAdminFail() {
            // Arrange
            String userIdToDelete = adminUser.getId(); // Employee tries to delete Admin
            String requesterRole = Role.ROLE_EMPLOYEE.name();
            // No need to mock repository as it should fail before

            // Act
            Mono<Void> result = userService.deleteUserById(userIdToDelete, requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectError(AccessDeniedException.class)
                    .verify();

            verify(userRepository, never()).findById(anyString()); // findById skipped
            verify(userRepository, never()).deleteById(anyString()); // deleteById skipped
        }

        @Test
        @DisplayName("Admin should fail to delete a non-existent user (Not Found)")
        void deleteUserById_AdminFail_NotFound() {
            // Arrange
            String userIdToDelete = "non-existent-id";
            String requesterRole = Role.ROLE_ADMIN.name();
            when(userRepository.findById(userIdToDelete)).thenReturn(Mono.empty()); // Simulate user not found

            // Act
            Mono<Void> result = userService.deleteUserById(userIdToDelete, requesterRole);

            // Assert
            StepVerifier.create(result)
                    .expectError(ResourceNotFoundException.class)
                    .verify();

            verify(userRepository).findById(userIdToDelete);
            verify(userRepository, never()).deleteById(anyString()); // deleteById skipped
        }
    }
}