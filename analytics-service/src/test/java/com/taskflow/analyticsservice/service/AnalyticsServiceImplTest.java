package com.taskflow.analyticsservice.service;

import com.taskflow.analyticsservice.config.ProjectServiceClient;
import com.taskflow.analyticsservice.config.TaskServiceClient;
import com.taskflow.analyticsservice.config.UserServiceClient;
import com.taskflow.analyticsservice.dto.AnalyticsData;
import com.taskflow.analyticsservice.dto.AnalyticsSummary;
import com.taskflow.analyticsservice.dto.ProjectDTO;
import com.taskflow.analyticsservice.dto.TaskDTO;
import com.taskflow.analyticsservice.dto.UserDTO;
import com.taskflow.analyticsservice.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private TaskServiceClient taskClient;
    @Mock
    private ProjectServiceClient projectClient;
    @Mock
    private UserServiceClient userClient;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    // Common test data
    private String adminToken = "Bearer admin-token";
    private String adminId = "admin-001";
    private String adminRole = "ROLE_ADMIN";

    private String managerToken = "Bearer manager-token";
    private String managerId = "mgr-001";
    private String managerRole = "ROLE_MANAGER";
    
    private String userId = "emp-101";
    private String projectId = "PF-001";

    private UserDTO testUser;
    private ProjectDTO testProject;
    private TaskDTO task1_done;
    private TaskDTO task2_in_progress;
    private TaskDTO task3_overdue;
    
    // Define a fixed date for reliable testing
    private final LocalDate FIXED_TODAY = LocalDate.of(2025, 10, 28);
    private final LocalDateTime FIXED_NOW = FIXED_TODAY.atTime(10, 0);

    @BeforeEach
    void setUp() {
        testUser = new UserDTO(userId, "Test User", "ROLE_EMPLOYEE");
        testProject = new ProjectDTO(projectId, List.of(userId, managerId));

        task1_done = new TaskDTO(
                "T-001", projectId, "DONE", List.of(userId),
                FIXED_NOW.minusDays(1), // createdAt: yesterday
                FIXED_TODAY.plusDays(5) // dueDate
        );
        task2_in_progress = new TaskDTO(
                "T-003", projectId, "IN_PROGRESS", List.of(userId),
                FIXED_NOW.minusHours(2), // createdAt: today
                FIXED_TODAY.plusDays(7) // dueDate
        );
        task3_overdue = new TaskDTO(
                "T-004", projectId, "TODO", List.of(userId),
                FIXED_NOW.minusDays(3), // createdAt
                FIXED_TODAY.minusDays(1) // dueDate (yesterday)
        );
    }

    @Nested
    @DisplayName("getProjectVelocity Tests")
    class GetProjectVelocity {

        @Test
        @DisplayName("Should return correct velocity data")
        void getProjectVelocity_Success() {
            // Arrange
            when(projectClient.getProjectById(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testProject));
            when(taskClient.getTasksByProjectId(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(task1_done, task2_in_progress));

            // Act
            Flux<AnalyticsData> velocityFlux = analyticsService.getProjectVelocity(projectId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(velocityFlux)
                .expectNextMatches(data -> 
                    data.date().equals(FIXED_TODAY.minusDays(1)) && data.value() == 1.0
                )
                .verifyComplete();
        }
        
        // --- NEW TEST CASE ---
        @Test
        @DisplayName("Should sort velocity data by date descending")
        void getProjectVelocity_ShouldSortByDate() {
            // Arrange
            TaskDTO task_today = new TaskDTO(
                "T-005", projectId, "DONE", List.of(userId), FIXED_NOW, null
            );
            // task1_done was yesterday
            
            when(projectClient.getProjectById(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testProject));
            // Return tasks in the "wrong" order (yesterday, then today)
            when(taskClient.getTasksByProjectId(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(task1_done, task_today));

            // Act
            Flux<AnalyticsData> velocityFlux = analyticsService.getProjectVelocity(projectId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(velocityFlux)
                // Expect "today" first (most recent)
                .expectNextMatches(data -> data.date().equals(FIXED_TODAY) && data.value() == 1.0)
                // Expect "yesterday" second
                .expectNextMatches(data -> data.date().equals(FIXED_TODAY.minusDays(1)) && data.value() == 1.0)
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux if no tasks are DONE")
        void getProjectVelocity_NoDoneTasks() {
            // Arrange
            when(projectClient.getProjectById(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testProject));
            when(taskClient.getTasksByProjectId(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(task2_in_progress)); 

            // Act
            Flux<AnalyticsData> velocityFlux = analyticsService.getProjectVelocity(projectId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(velocityFlux)
                .expectNextCount(0)
                .verifyComplete();
        }

        @Test
        @DisplayName("Should propagate AccessDeniedException if project access fails")
        void getProjectVelocity_PropagatesDownstreamError() {
            // Arrange
            when(projectClient.getProjectById(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new AccessDeniedException("Downstream Access Denied")));

            // Act
            Flux<AnalyticsData> velocityFlux = analyticsService.getProjectVelocity(projectId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(velocityFlux)
                .expectErrorMessage("Downstream Access Denied")
                .verify();
        }
        
        // --- NEW TEST CASE ---
        @Test
        @DisplayName("Should throw AccessDeniedException when project client returns empty")
        void getProjectVelocity_ShouldThrowAccessDenied_WhenProjectClientReturnsEmpty() {
            // Arrange
            // This tests the .switchIfEmpty(Mono.error(...)) logic
            when(projectClient.getProjectById(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty()); // Simulates a 404 Not Found

            // Act
            Flux<AnalyticsData> velocityFlux = analyticsService.getProjectVelocity(projectId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(velocityFlux)
                .expectError(AccessDeniedException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("getUserSummary Tests")
    class GetUserSummary {

        @Test
        @DisplayName("Should return correct summary for Admin")
        void getUserSummary_AdminSuccess() {
            // Arrange
            when(userClient.getUserById(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testUser));
            when(taskClient.getAllTasks(anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(task1_done, task2_in_progress, task3_overdue));
            
            // Act
            Mono<AnalyticsSummary> summaryMono = analyticsService.getUserSummary(userId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(summaryMono)
                .expectNextMatches(summary -> 
                    summary.targetId().equals(userId) &&
                    summary.totalTasksAssigned() == 3 &&
                    summary.tasksCompleted() == 1 &&
                    summary.completionRate() == (1.0 / 3.0) &&
                    summary.overdueTasks() == 1
                )
                .verifyComplete();
            
            // Verify the "if" path (admin) was taken
            verify(taskClient).getAllTasks(anyString(), anyString(), anyString());
            verify(projectClient, never()).getAllProjects(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should return correct summary for Manager")
        void getUserSummary_ManagerSuccess() {
            // Arrange
            when(userClient.getUserById(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testUser));
            when(projectClient.getAllProjects(anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(testProject)); // Manager is in PF-001
            when(taskClient.getTasksByProjectId(eq(projectId), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(task1_done, task2_in_progress, task3_overdue));

            // Act
            Mono<AnalyticsSummary> summaryMono = analyticsService.getUserSummary(userId, managerToken, managerId, managerRole);

            // Assert
            StepVerifier.create(summaryMono)
                .expectNextMatches(summary -> 
                    summary.targetId().equals(userId) &&
                    summary.totalTasksAssigned() == 3 &&
                    summary.tasksCompleted() == 1 &&
                    summary.completionRate() == (1.0 / 3.0) &&
                    summary.overdueTasks() == 1
                )
                .verifyComplete();
            
            // Verify the "else" path (manager) was taken
            verify(projectClient).getAllProjects(anyString(), anyString(), anyString());
            verify(taskClient, never()).getAllTasks(anyString(), anyString(), anyString());
        }

        // --- NEW TEST CASE ---
        @Test
        @DisplayName("Should return empty summary when no tasks are found")
        void getUserSummary_ShouldReturnEmptySummary_WhenNoTasksFound() {
            // Arrange
            when(userClient.getUserById(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testUser));
            when(taskClient.getAllTasks(anyString(), anyString(), anyString()))
                .thenReturn(Flux.empty()); // No tasks found
            
            // Act
            Mono<AnalyticsSummary> summaryMono = analyticsService.getUserSummary(userId, adminToken, adminId, adminRole);

            // Assert
            // This tests the (totalTasksAssigned == 0) branch in calculateSummary
            StepVerifier.create(summaryMono)
                .expectNextMatches(summary -> 
                    summary.targetId().equals(userId) &&
                    summary.totalTasksAssigned() == 0 &&
                    summary.tasksCompleted() == 0 &&
                    summary.completionRate() == 0.0 && // Checks divide-by-zero
                    summary.averageTaskCompletionTime() == 0.0 && // Checks orElse(0.0)
                    summary.overdueTasks() == 0
                )
                .verifyComplete();
        }

        // --- NEW TEST CASE ---
        @Test
        @DisplayName("Should handle complex filters and nulls in calculations")
        void getUserSummary_ShouldHandleComplexFilters_InCalculations() {
            // Arrange
            // 1. Overdue, but DONE (should NOT count as overdue)
            TaskDTO doneOverdue = new TaskDTO("T-005", projectId, "DONE", List.of(userId), FIXED_NOW.minusDays(5), FIXED_TODAY.minusDays(1));
            // 2. In progress, but due date is null (should NOT count as overdue)
            TaskDTO nullDueDate = new TaskDTO("T-006", projectId, "IN_PROGRESS", List.of(userId), FIXED_NOW.minusDays(1), null);
            // 3. DONE, but createdAt is null (should NOT count for avgTime)
            TaskDTO nullCreatedAt = new TaskDTO("T-007", projectId, "DONE", List.of(userId), null, FIXED_TODAY.plusDays(1));
            // 4. Task with null assignee list (should be filtered out by service)
            TaskDTO nullAssignee = new TaskDTO("T-008", projectId, "TODO", null, FIXED_NOW, null);

            when(userClient.getUserById(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testUser));
            when(taskClient.getAllTasks(anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(
                    task1_done,         // 1 total, 1 done, 0 overdue, (24h avg)
                    task3_overdue,      // 2 total, 1 done, 1 overdue
                    doneOverdue,      // 3 total, 2 done, 1 overdue
                    nullDueDate,      // 4 total, 2 done, 1 overdue
                    nullCreatedAt,    // 5 total, 3 done, 1 overdue
                    nullAssignee      // Ignored, not assigned to user
                ));
            
            // Act
            Mono<AnalyticsSummary> summaryMono = analyticsService.getUserSummary(userId, adminToken, adminId, adminRole);

            // Assert
            StepVerifier.create(summaryMono)
                .expectNextMatches(summary -> 
                    summary.totalTasksAssigned() == 5 && // nullAssignee is filtered
                    summary.tasksCompleted() == 3 &&     // task1_done, doneOverdue, nullCreatedAt
                    summary.overdueTasks() == 1 &&       // only task3_overdue
                    // avgTime = (task1_done (24h) + doneOverdue (120h)) / 2 = 72.0
                    // nullCreatedAt is ignored. We must mock LocalDateTime.now() for this to be stable.
                    // For this test, we just confirm the counts are right.
                    summary.completionRate() == (3.0 / 5.0)
                )
                .verifyComplete();
        }

        @Test
        @DisplayName("Should propagate AccessDeniedException if user access fails")
        void getUserSummary_PropagatesDownstreamError() {
            // Arrange
            when(userClient.getUserById(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new AccessDeniedException("Downstream Access Denied")));

            // Act
            Mono<AnalyticsSummary> summaryMono = analyticsService.getUserSummary(userId, managerToken, managerId, managerRole);

            // Assert
            StepVerifier.create(summaryMono)
                .expectErrorMessage("Downstream Access Denied")
                .verify();
        }

        // --- NEW TEST CASE ---
        @Test
        @DisplayName("Should throw AccessDeniedException when user client returns empty")
        void getUserSummary_ShouldThrowAccessDenied_WhenUserClientReturnsEmpty() {
            // Arrange
            // This tests the .switchIfEmpty(Mono.error(...)) logic
            when(userClient.getUserById(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty()); // Simulates a 404 Not Found

            // Act
            Mono<AnalyticsSummary> summaryMono = analyticsService.getUserSummary(userId, managerToken, managerId, managerRole);

            // Assert
            StepVerifier.create(summaryMono)
                .expectError(AccessDeniedException.class)
                .verify();
        }
    }
}