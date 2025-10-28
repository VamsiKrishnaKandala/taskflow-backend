package com.taskflow.analyticsservice.handler;

import com.taskflow.analyticsservice.dto.AnalyticsData;
import com.taskflow.analyticsservice.dto.AnalyticsSummary;
import com.taskflow.analyticsservice.exception.AccessDeniedException;
// import com.taskflow.analyticsservice.exception.GlobalExceptionHandler; // REMOVE THIS
import com.taskflow.analyticsservice.router.RouterConfig;
import com.taskflow.analyticsservice.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource; // <-- IMPORT THIS
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest
// --- FIX 1: Remove GlobalExceptionHandler, it's not used for functional routes ---
@ContextConfiguration(classes = {RouterConfig.class, AnalyticsHandler.class})
// --- FIX 2: Add the base-path property to the test context ---
@TestPropertySource(properties = "spring.webflux.base-path=/api/v1")
class AnalyticsHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AnalyticsService analyticsService;

    // --- Test Data ---
    private AnalyticsData dataPoint1 = new AnalyticsData(LocalDate.now(), 5.0, "tasksCompleted");
    private AnalyticsSummary summary1 = new AnalyticsSummary("emp-101", 10, 5, 0.5, 24.0, 1);

    @Nested
    @DisplayName("GET /analytics/project/{projectId}/velocity Tests")
    class GetProjectVelocity {

        @Test
        @DisplayName("Should return 200 OK with data for Admin")
        void getProjectVelocity_AdminSuccess() {
            // Arrange
            when(analyticsService.getProjectVelocity(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(dataPoint1));
            
            // Act & Assert
            // This URI will now be found
            webTestClient.get().uri("/api/v1/analytics/project/PF-001/velocity") 
                .header("X-User-Id", "admin-001")
                .header("X-User-Role", "ROLE_ADMIN")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isOk() // <-- This will pass
                .expectBodyList(AnalyticsData.class).hasSize(1).contains(dataPoint1);
        }

        @Test
        @DisplayName("Should return 403 Forbidden for Employee")
        void getProjectVelocity_EmployeeFail() {
            // Arrange: No mock needed
            
            // Act & Assert
            webTestClient.get().uri("/api/v1/analytics/project/PF-001/velocity") 
                .header("X-User-Id", "emp-101")
                .header("X-User-Role", "ROLE_EMPLOYEE")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isForbidden() // <-- This will pass
                .expectBody()
                .jsonPath("$.message").isEqualTo("Access Denied")
                .jsonPath("$.details[0]").isEqualTo("Only ADMIN or MANAGER users can view project analytics.");
        }
    }

    @Nested
    @DisplayName("GET /analytics/user/{userId}/summary Tests")
    class GetUserSummary {

        @Test
        @DisplayName("Should return 200 OK with data for Manager")
        void getUserSummary_ManagerSuccess() {
            // Arrange
            when(analyticsService.getUserSummary(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(summary1));
            
            // Act & Assert
            webTestClient.get().uri("/api/v1/analytics/user/emp-101/summary") 
                .header("X-User-Id", "mgr-001")
                .header("X-User-Role", "ROLE_MANAGER")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isOk() // <-- This will pass
                .expectBody(AnalyticsSummary.class).isEqualTo(summary1);
        }

        @Test
        @DisplayName("Should return 403 Forbidden for Employee")
        void getUserSummary_EmployeeFail() {
            // Arrange: No mock needed
            
            // Act & Assert
            webTestClient.get().uri("/api/v1/analytics/user/emp-101/summary") 
                .header("X-User-Id", "emp-101")
                .header("X-User-Role", "ROLE_EMPLOYEE")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isForbidden() // <-- This will pass
                .expectBody()
                .jsonPath("$.message").isEqualTo("Access Denied")
                .jsonPath("$.details[0]").isEqualTo("Only ADMIN or MANAGER users can view user analytics.");
        }

        @Test
        @DisplayName("Should return 403 if service denies access (e.g., user not found)")
        void getUserSummary_ServiceDenial() {
            // Arrange
            when(analyticsService.getUserSummary(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new AccessDeniedException("User not found or access denied")));
            
            // Act & Assert
            webTestClient.get().uri("/api/v1/analytics/user/emp-101/summary") 
                .header("X-User-Id", "admin-001")
                .header("X-User-Role", "ROLE_ADMIN")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isForbidden() // <-- This will pass
                .expectBody()
                .jsonPath("$.message").isEqualTo("Access Denied")
                .jsonPath("$.details[0]").isEqualTo("User not found or access denied");
        }
    }
    @Test
    @DisplayName("Should return 500 on unexpected service exception")
    void getUserSummary_ServiceRuntimeException() {
        // Arrange
        when(analyticsService.getUserSummary(anyString(), anyString(), anyString(), anyString()))
            // Mock a generic, unexpected error
            .thenReturn(Mono.error(new RuntimeException("Database is down"))); 
        
        // Act & Assert
        webTestClient.get().uri("/api/v1/analytics/user/emp-101/summary") 
            .header("X-User-Id", "admin-001")
            .header("X-User-Role", "ROLE_ADMIN")
            .header("Authorization", "Bearer token")
            .exchange()
            .expectStatus().is5xxServerError() // Expect 500
            .expectBody()
            .jsonPath("$.message").isEqualTo("Error processing summary")
            .jsonPath("$.details[0]").isEqualTo("Database is down");
    }
    
}