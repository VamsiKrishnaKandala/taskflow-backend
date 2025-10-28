package com.taskflow.analyticsservice.service;

import com.taskflow.analyticsservice.config.ProjectServiceClient;
import com.taskflow.analyticsservice.config.TaskServiceClient;
import com.taskflow.analyticsservice.config.UserServiceClient;

import com.taskflow.analyticsservice.dto.AnalyticsData;
import com.taskflow.analyticsservice.dto.AnalyticsSummary;
import com.taskflow.analyticsservice.dto.TaskDTO; // Ensure this DTO is in the client package
import com.taskflow.analyticsservice.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final TaskServiceClient taskClient;
    private final ProjectServiceClient projectClient;
    private final UserServiceClient userClient;

    // Helper for Role Checking
    private boolean isAdmin(String requesterRole) { return "ROLE_ADMIN".equals(requesterRole); }
    private boolean isManager(String requesterRole) { return "ROLE_MANAGER".equals(requesterRole); }

    /**
     * Calculates project velocity.
     * Fetches all tasks for the project, groups them by completion date, and counts them.
     */
    @Override
    public Flux<AnalyticsData> getProjectVelocity(String projectId, String authorizationHeader, String requesterId, String requesterRole) {
        log.info("Calculating project velocity for {} by user {}", projectId, requesterId);

        // 1. Authorize: Check if user can access this project
        return projectClient.getProjectById(projectId, authorizationHeader, requesterId, requesterRole)
                .switchIfEmpty(Mono.error(new AccessDeniedException("Project not found or access denied")))
                // 2. If authorized, fetch the tasks for that project
                .flatMapMany(project -> 
                    taskClient.getTasksByProjectId(projectId, authorizationHeader, requesterId, requesterRole)
                )
                // 3. Filter for tasks that are "DONE"
                .filter(task -> "DONE".equalsIgnoreCase(task.status()))
                // 4. Group tasks by the date they were created (or completed, if available)
                .collect(Collectors.groupingBy(
                        task -> task.createdAt().toLocalDate(), // Group by date
                        Collectors.counting() // Count tasks per date
                ))
                // 5. Transform the Map<LocalDate, Long> into a Flux<AnalyticsData>
                .flatMapMany(tasksByDate -> Flux.fromIterable(tasksByDate.entrySet()))
                .map(entry -> new AnalyticsData(
                        entry.getKey(), 
                        entry.getValue().doubleValue(), // The count
                        "tasksCompleted"
                ))
                .sort((a, b) -> b.date().compareTo(a.date())); // Sort by most recent date first
    }

    /**
     * Calculates a performance summary for a specific user.
     */
    @Override
    public Mono<AnalyticsSummary> getUserSummary(String userId, String authorizationHeader, String requesterId, String requesterRole) {
        log.info("Calculating user summary for {} by user {}", userId, requesterId);
        
        // 1. Authorize: Check if the requester can view this user's profile
        return userClient.getUserById(userId, authorizationHeader, requesterId, requesterRole)
            .switchIfEmpty(Mono.error(new AccessDeniedException("User not found or access denied")))
            // 2. Decide which tasks to fetch
            .flatMap(userDTO -> {
                Flux<TaskDTO> tasksFlux;
                if (isAdmin(requesterRole)) {
                    // An Admin can get all tasks and we will filter
                    log.debug("Admin detected, fetching all tasks for user summary");
                    tasksFlux = taskClient.getAllTasks(authorizationHeader, requesterId, requesterRole);
                } else {
                    // A Manager must fetch projects they are in, then tasks from those projects
                    log.debug("Manager detected, fetching tasks from their projects for user summary");
                    tasksFlux = projectClient.getAllProjects(authorizationHeader, requesterId, requesterRole) // Get manager's projects
                        .flatMap(project -> 
                            // Get tasks for each project
                            taskClient.getTasksByProjectId(project.id(), authorizationHeader, requesterId, requesterRole)
                        );
                }

                // 3. Filter all fetched tasks to find those assigned to the target user
                return tasksFlux
                    .filter(task -> task.assigneeIdsList() != null && task.assigneeIdsList().contains(userId))
                    .collectList();
            })
            // 4. Perform the calculations on the final list of tasks
            .map(tasks -> calculateSummary(userId, tasks));
    }
    
    /**
     * Helper method to perform the final calculations on a list of tasks.
     */
    private AnalyticsSummary calculateSummary(String userId, List<TaskDTO> tasks) {
        long totalTasksAssigned = tasks.size();
        
        long tasksCompleted = tasks.stream()
                .filter(task -> "DONE".equalsIgnoreCase(task.status()))
                .count();

        double completionRate = (totalTasksAssigned == 0) ? 0 : (double) tasksCompleted / totalTasksAssigned;

        long overdueTasks = tasks.stream()
                .filter(task -> !"DONE".equalsIgnoreCase(task.status()) && 
                               task.dueDate() != null && 
                               task.dueDate().isBefore(LocalDate.now()))
                .count();
                
        // Calculate Average Task Completion Time (simple version)
        // Note: We need a 'completedAt' field on the TaskDTO for this to be accurate.
        // We will mock this for now by using createdAt.
        double avgTime = tasks.stream()
                .filter(task -> "DONE".equalsIgnoreCase(task.status()) && task.createdAt() != null)
                .mapToLong(task -> ChronoUnit.HOURS.between(task.createdAt(), LocalDateTime.now())) // Mock: use (Now - CreatedAt)
                .average()
                .orElse(0.0);

        return new AnalyticsSummary(
            userId,
            totalTasksAssigned,
            tasksCompleted,
            completionRate,
            avgTime,
            overdueTasks
        );
    }
}