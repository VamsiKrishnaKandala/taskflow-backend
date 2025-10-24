package com.taskflow.userservice.handler;

import com.taskflow.userservice.config.RouterConfig;
import com.taskflow.userservice.dto.*;
import com.taskflow.userservice.exception.AccessDeniedException;
import com.taskflow.userservice.exception.DuplicateResourceException;
import com.taskflow.userservice.exception.GlobalExceptionHandler; // <-- IMPORT THIS
import com.taskflow.userservice.exception.InvalidLoginException;
import com.taskflow.userservice.exception.ResourceNotFoundException;
import com.taskflow.userservice.model.Role;
import com.taskflow.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus; // <-- Ensure HttpStatus is imported
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserHandler using WebTestClient.
 * Mocks the UserService to isolate handler and routing logic.
 */
@WebFluxTest
// Add GlobalExceptionHandler.class here to load it into the test context
@ContextConfiguration(classes = {RouterConfig.class, UserHandler.class, GlobalExceptionHandler.class}) // <-- MODIFIED LINE
@Import(TestValidationConfiguration.class) // Import validation configuration
class UserHandlerTest {

    @Autowired
    private WebTestClient webTestClient; // Test client for WebFlux

    @MockBean // Mock the UserService dependency
    private UserService userService;

    // Test data
    private UserResponse adminUserResponse;
    private UserResponse employeeUserResponse;
    private UserCreateRequest adminCreateRequest;
    private AuthRequest adminAuthRequest;
    private String adminToken = "Bearer valid.admin.token";
    private String employeeToken = "Bearer valid.employee.token";


    @BeforeEach
    void setUp() {
        adminUserResponse = UserResponse.builder()
                .id("admin-001")
                .name("Admin User")
                .email("admin@company.com")
                .role(Role.ROLE_ADMIN)
                .activeProjectIds(Collections.emptyList())
                .build();
        employeeUserResponse = UserResponse.builder()
                .id("emp-101")
                .name("Employee User")
                .email("employee@company.com")
                .role(Role.ROLE_EMPLOYEE)
                .activeProjectIds(List.of("proj-B"))
                .build();
        adminCreateRequest = UserCreateRequest.builder()
                .id(adminUserResponse.getId())
                .name(adminUserResponse.getName())
                .email(adminUserResponse.getEmail())
                .password("rawPasswordAdmin")
                .role(adminUserResponse.getRole())
                .build();
        adminAuthRequest = new AuthRequest();
        adminAuthRequest.setEmail(adminUserResponse.getEmail());
        adminAuthRequest.setPassword("rawPasswordAdmin");

    }

    // --- Registration Tests (POST /users) ---
    @Nested
    @DisplayName("POST /users Tests (Registration)")
    class RegisterUserTests {
        @Test
        @DisplayName("Success - Should return 201 Created")
        void handleRegisterUser_Success() {
            when(userService.registerUser(any(UserCreateRequest.class))).thenReturn(Mono.just(adminUserResponse));

            webTestClient.post().uri("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(adminCreateRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(UserResponse.class).isEqualTo(adminUserResponse);
        }

        @Test
        @DisplayName("Fail - Should return 409 Conflict on duplicate")
        void handleRegisterUser_Duplicate() {
            when(userService.registerUser(any(UserCreateRequest.class)))
                    .thenReturn(Mono.error(new DuplicateResourceException("Email already exists")));

            webTestClient.post().uri("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(adminCreateRequest)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT); // Check status code is 409
                    // .expectBody()... // Optionally check error body details from GlobalExceptionHandler
        }

        @Test
        @DisplayName("Fail - Should return 400 Bad Request on validation error")
        void handleRegisterUser_ValidationFailure() {
            UserCreateRequest invalidRequest = UserCreateRequest.builder().id("").email("bad").build(); // Invalid data

            webTestClient.post().uri("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalidRequest)
                    .exchange()
                    .expectStatus().isBadRequest(); // Check status code is 400
                    // .expectBody()... // Optionally check error body details from GlobalExceptionHandler
        }
    }


    // --- Login Tests (POST /auth/login) ---
    @Nested
    @DisplayName("POST /auth/login Tests")
    class LoginUserTests {
        @Test
        @DisplayName("Success - Should return 200 OK with AuthResponse")
        void handleLoginUser_Success() {
            AuthResponse authResponse = AuthResponse.builder().token("token").userId("id").build();
            when(userService.loginUser(any(AuthRequest.class))).thenReturn(Mono.just(authResponse));

            webTestClient.post().uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(adminAuthRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(AuthResponse.class).isEqualTo(authResponse);
        }

        @Test
        @DisplayName("Fail - Should return 401 Unauthorized on invalid credentials")
        void handleLoginUser_InvalidCredentials() {
            when(userService.loginUser(any(AuthRequest.class)))
                    .thenReturn(Mono.error(new InvalidLoginException("Invalid credentials")));

            webTestClient.post().uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(adminAuthRequest)
                    .exchange()
                    .expectStatus().isUnauthorized(); // Check status code is 401 (handled by GlobalExceptionHandler)
                    // .expectBody()...
        }
         @Test
        @DisplayName("Fail - Should return 400 Bad Request on validation error")
        void handleLoginUser_ValidationFailure() {
            AuthRequest invalidRequest = new AuthRequest();
            invalidRequest.setEmail("bad"); // Invalid email

            webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
        }
    }


    // --- Get User By ID Tests (GET /users/{id}) ---
    @Nested
    @DisplayName("GET /users/{id} Tests")
    class GetUserByIdTests {
        @Test
        @DisplayName("Success - Admin gets other user, Should return 200 OK")
        void handleGetUserById_AdminGetsOther() {
            String requestedId = employeeUserResponse.getId();
            when(userService.getUserById(eq(requestedId), eq("admin-id-from-token"), eq("ROLE_ADMIN")))
                .thenReturn(Mono.just(employeeUserResponse));

            webTestClient.get().uri("/users/{id}", requestedId)
                    .header("X-User-Id", "admin-id-from-token") // Simulate header from gateway
                    .header("X-User-Role", "ROLE_ADMIN")       // Simulate header from gateway
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserResponse.class).isEqualTo(employeeUserResponse);
        }

        @Test
        @DisplayName("Success - User gets self, Should return 200 OK")
        void handleGetUserById_UserGetsSelf() {
             String requestedId = employeeUserResponse.getId();
             when(userService.getUserById(eq(requestedId), eq(requestedId), eq("ROLE_EMPLOYEE")))
                 .thenReturn(Mono.just(employeeUserResponse));

             webTestClient.get().uri("/users/{id}", requestedId)
                     .header("X-User-Id", requestedId)          // Simulate header from gateway (matches path id)
                     .header("X-User-Role", "ROLE_EMPLOYEE")    // Simulate header from gateway
                     .exchange()
                     .expectStatus().isOk()
                     .expectBody(UserResponse.class).isEqualTo(employeeUserResponse);
        }

        @Test
        @DisplayName("Fail - User gets other, Should return 403 Forbidden")
        void handleGetUserById_UserFailsGetOther() {
             String requestedId = adminUserResponse.getId(); // Employee trying to get Admin
             String requesterId = employeeUserResponse.getId();
             String requesterRole = "ROLE_EMPLOYEE";
             when(userService.getUserById(eq(requestedId), eq(requesterId), eq(requesterRole)))
                 .thenReturn(Mono.error(new AccessDeniedException("Access Denied"))); // Mock service throwing error

             webTestClient.get().uri("/users/{id}", requestedId)
                     .header("X-User-Id", requesterId)
                     .header("X-User-Role", requesterRole)
                     .exchange()
                     .expectStatus().isForbidden(); // Check status code is 403 (handled by GlobalExceptionHandler or handler)
                     // .expectBody()...
        }

        @Test
        @DisplayName("Fail - User not found, Should return 404 Not Found")
        void handleGetUserById_NotFound() {
            String requestedId = "not-found-id";
            when(userService.getUserById(eq(requestedId), anyString(), anyString()))
                .thenReturn(Mono.error(new ResourceNotFoundException("Not Found")));

            webTestClient.get().uri("/users/{id}", requestedId)
                    .header("X-User-Id", "any-id")
                    .header("X-User-Role", "ROLE_ADMIN") // Admin trying to get non-existent user
                    .exchange()
                    .expectStatus().isNotFound(); // Check status code is 404 (handled by GlobalExceptionHandler or handler)
                    // .expectBody()...
        }
    }


    // --- List Users Tests (GET /users/list/all) ---
    @Nested
    @DisplayName("GET /users/list/all Tests")
    class ListUsersTests {
        @Test
        @DisplayName("Success - Admin lists users, Should return 200 OK with list")
        void handleListUsers_AdminSuccess() {
            when(userService.findAllUsers(eq("ROLE_ADMIN")))
                .thenReturn(Flux.just(adminUserResponse, employeeUserResponse));

            webTestClient.get().uri("/users/list/all") // Using updated path
                    .header("X-User-Role", "ROLE_ADMIN") // Simulate header
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(UserResponse.class).hasSize(2).contains(adminUserResponse, employeeUserResponse);
        }

        @Test
        @DisplayName("Fail - Employee lists users, Should return 403 Forbidden")
        void handleListUsers_EmployeeFail() {
            when(userService.findAllUsers(eq("ROLE_EMPLOYEE")))
                .thenReturn(Flux.error(new AccessDeniedException("Admin only"))); // Mock service throwing error

            webTestClient.get().uri("/users/list/all") // Using updated path
                    .header("X-User-Role", "ROLE_EMPLOYEE") // Simulate header
                    .exchange()
                    .expectStatus().isForbidden(); // Check status code is 403 (handled by GlobalExceptionHandler or handler)
                    // .expectBody()...
        }
    }

    // --- Update Role Tests (PUT /users/{id}/role) ---
    @Nested
    @DisplayName("PUT /users/{id}/role Tests")
    class UpdateRoleTests {
         @Test
        @DisplayName("Success - Should return 200 OK with updated user")
        void handleUpdateUserRole_Success() {
            String userId = employeeUserResponse.getId();
            UserRoleUpdateRequest request = new UserRoleUpdateRequest();
            request.setNewRole(Role.ROLE_MANAGER);
            UserResponse updatedResponse = employeeUserResponse.builder().role(Role.ROLE_MANAGER).build(); // Simulate updated user

            when(userService.updateUserRole(eq(userId), eq(Role.ROLE_MANAGER)))
                .thenReturn(Mono.just(updatedResponse));

            webTestClient.put().uri("/users/{id}/role", userId)
                    .header("X-User-Role", "ROLE_ADMIN") // Assume Admin is performing update
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserResponse.class).isEqualTo(updatedResponse);
        }

        @Test
        @DisplayName("Fail - User not found, Should return 404 Not Found")
        void handleUpdateUserRole_NotFound() {
            String userId = "not-found-id";
             UserRoleUpdateRequest request = new UserRoleUpdateRequest();
             request.setNewRole(Role.ROLE_MANAGER);

            when(userService.updateUserRole(eq(userId), any(Role.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("Not Found")));

            webTestClient.put().uri("/users/{id}/role", userId)
                    .header("X-User-Role", "ROLE_ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Fail - Validation error, Should return 400 Bad Request")
        void handleUpdateUserRole_ValidationFail() {
             String userId = employeeUserResponse.getId();
             UserRoleUpdateRequest invalidRequest = new UserRoleUpdateRequest();
             invalidRequest.setNewRole(null); // Invalid null role

             webTestClient.put().uri("/users/{id}/role", userId)
                     .header("X-User-Role", "ROLE_ADMIN")
                     .contentType(MediaType.APPLICATION_JSON)
                     .bodyValue(invalidRequest)
                     .exchange()
                     .expectStatus().isBadRequest();
        }
        // Add 403 test if service layer implements non-admin check for update role
    }

    // --- Logout Tests (POST /auth/logout) ---
     @Nested
    @DisplayName("POST /auth/logout Tests")
    class LogoutUserTests {
        @Test
        @DisplayName("Success - Should return 200 OK")
        void handleLogoutUser_Success() {
            when(userService.logoutUser(eq(adminToken))).thenReturn(Mono.empty()); // Mock service success (Mono<Void>)

            webTestClient.post().uri("/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, adminToken) // Pass token in header
                    .exchange()
                    .expectStatus().isOk(); // Expect 200 OK, empty body
        }

         @Test
        @DisplayName("Fail - Missing token, Should return 401 Unauthorized")
        void handleLogoutUser_Fail_MissingToken() {
             // Mock service to throw expected exception from handler check
             when(userService.logoutUser(anyString()))
                 .thenReturn(Mono.error(new InvalidLoginException("Missing or invalid Authorization header for logout")));

            webTestClient.post().uri("/auth/logout")
                    // No Authorization header sent
                    .exchange()
                    .expectStatus().isUnauthorized(); // Expect 401 (handled by GlobalExceptionHandler)
        }
    }


    // --- Delete User Tests (DELETE /users/{id}) ---
     @Nested
    @DisplayName("DELETE /users/{id} Tests")
    class DeleteUserTests {
        @Test
        @DisplayName("Success - Admin deletes user, Should return 204 No Content")
        void handleDeleteUser_AdminSuccess() {
            String userIdToDelete = employeeUserResponse.getId();
            when(userService.deleteUserById(eq(userIdToDelete), eq("ROLE_ADMIN")))
                .thenReturn(Mono.empty()); // Mock service success (Mono<Void>)

            webTestClient.delete().uri("/users/{id}", userIdToDelete)
                    .header("X-User-Role", "ROLE_ADMIN") // Simulate header
                    .exchange()
                    .expectStatus().isNoContent(); // Expect 204
        }

        @Test
        @DisplayName("Fail - Employee deletes user, Should return 403 Forbidden")
        void handleDeleteUser_EmployeeFail() {
             String userIdToDelete = adminUserResponse.getId();
             when(userService.deleteUserById(eq(userIdToDelete), eq("ROLE_EMPLOYEE")))
                 .thenReturn(Mono.error(new AccessDeniedException("Admin only"))); // Mock service throwing error

             webTestClient.delete().uri("/users/{id}", userIdToDelete)
                     .header("X-User-Role", "ROLE_EMPLOYEE") // Simulate header
                     .exchange()
                     .expectStatus().isForbidden(); // Expect 403
        }

        @Test
        @DisplayName("Fail - User not found, Should return 404 Not Found")
        void handleDeleteUser_NotFound() {
             String userIdToDelete = "not-found-id";
             when(userService.deleteUserById(eq(userIdToDelete), eq("ROLE_ADMIN")))
                 .thenReturn(Mono.error(new ResourceNotFoundException("Not Found")));

             webTestClient.delete().uri("/users/{id}", userIdToDelete)
                     .header("X-User-Role", "ROLE_ADMIN")
                     .exchange()
                     .expectStatus().isNotFound(); // Expect 404
        }
    }
} // End of UserHandlerTest