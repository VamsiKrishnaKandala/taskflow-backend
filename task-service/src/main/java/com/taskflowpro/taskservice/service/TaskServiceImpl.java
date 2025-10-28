package com.taskflowpro.taskservice.service;

import com.taskflowpro.taskservice.client.ProjectServiceClientWebClient;
import com.taskflowpro.taskservice.client.UserServiceClientWebClient;
import com.taskflowpro.taskservice.client.NotificationServiceClientWebClient;
import com.taskflowpro.taskservice.dto.*;
import com.taskflowpro.taskservice.exception.AccessDeniedException;
import com.taskflowpro.taskservice.exception.InvalidTaskDataException;
import com.taskflowpro.taskservice.exception.TaskNotFoundException;
import com.taskflowpro.taskservice.model.Task;
import com.taskflowpro.taskservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectServiceClientWebClient projectServiceClient;
    private final UserServiceClientWebClient userServiceClient;
    private final NotificationServiceClientWebClient notificationClient;
    private final Sinks.Many<String> taskEventSink;

    // --- Helper for Role Checking ---
    private boolean isAdmin(String requesterRole) { return "ROLE_ADMIN".equals(requesterRole); }
    private boolean isManager(String requesterRole) { return "ROLE_MANAGER".equals(requesterRole); }
    
    private void publishEvent(String event) {
        Sinks.EmitResult result = taskEventSink.tryEmitNext(event);
        if (result.isFailure()) { log.error("Failed to publish task event '{}': {}", event, result); } 
        else { log.info("Task event published: {}", event); }
    }

    // --- MODIFIED: Needs auth headers to call the secured endpoint ---
    private Mono<Void> sendNotificationSafe(String recipientUserId, Map<String, Object> basePayload, String authorizationHeader, String requesterId, String requesterRole) {
        Map<String, Object> payload = new HashMap<>(basePayload);
        payload.put("recipientUserId", recipientUserId);

        return notificationClient.sendNotificationEvent(payload, authorizationHeader, requesterId, requesterRole)
                .doOnError(ex -> log.error("Notification to user {} failed: {}", recipientUserId, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    // --- MODIFIED: Needs auth headers to call the secured endpoint ---
    private Mono<Void> sendNotificationsToAssignees(List<String> assigneeIds, Map<String, Object> basePayload, String authorizationHeader, String requesterId, String requesterRole) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(assigneeIds)
                .flatMap(id -> sendNotificationSafe(id, basePayload, authorizationHeader, requesterId, requesterRole))
                .then();
    }

    // -------------------- CREATE --------------------
    @Override
    public Mono<TaskResponseDTO> createTask(TaskRequestDTO request, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to create new task for project {}: by User: {}, Role: {}", request.getProjectId(), requesterId, requesterRole);

        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to create a task.", requesterId, requesterRole);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can create tasks."));
        }
        request.setCreatedBy(requesterId);

        return validateProjectAndAssignees(request.getProjectId(), request.getAssigneeIdsList(), requesterId, requesterRole, authorizationHeader)
                .then(taskRepository.findAll()
                        .map(Task::getId)
                        .filter(id -> id != null && id.startsWith("TF-"))
                        .map(id -> Integer.parseInt(id.substring(3)))
                        .sort()
                        .last(0))
                .defaultIfEmpty(0)
                .flatMap(max -> {
                    String nextId = String.format("TF-%03d", max + 1);
                    Task task = Task.builder()
                            .id(nextId)
                            .projectId(request.getProjectId())
                            .title(request.getTitle())
                            .description(request.getDescription())
                            .status(request.getStatus() != null ? request.getStatus() : "TODO")
                            .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                            .assigneeIdsList(request.getAssigneeIdsList())
                            .tagsList(request.getTagsList())
                            .dueDate(request.getDueDate())
                            .createdBy(request.getCreatedBy())
                            .createdAt(LocalDateTime.now())
                            .isNew(true)
                            .build();

                    task.serializeLists(); // Fix for NULL columns

                    return taskRepository.save(task)
                            .flatMap(saved -> {
                                saved.deserializeLists();
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("message", "New Task Assigned: " + saved.getTitle());
                                payload.put("taskId", saved.getId());
                                payload.put("eventType", "TASK_CREATED");
                                payload.put("taskTitle", saved.getTitle());
                                payload.put("projectId", saved.getProjectId());
                                
                                // --- PASS AUTH HEADERS ---
                                Mono<Void> notificationMono = sendNotificationsToAssignees(saved.getAssigneeIdsList(), payload, authorizationHeader, requesterId, requesterRole);

                                return notificationMono
                                        .doOnSuccess(v -> {
                                            publishEvent("TASK_CREATED:" + saved.getId() + ":project:" + saved.getProjectId());
                                            log.info("Task created successfully with ID: {}", saved.getId());
                                        })
                                        .thenReturn(TaskResponseDTO.fromEntity(saved));
                            })
                            .doOnError(ex -> log.error("Error creating task", ex));
                });
    }

    // -------------------- READ --------------------
    @Override
    public Mono<TaskResponseDTO> getTaskById(String id, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Fetching task ID: {} by User: {}, Role: {}", id, requesterId, requesterRole);
        
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> {
                    task.deserializeLists(); // FIX for auth check
                    
                    boolean isAdminOrManager = isAdmin(requesterRole) || isManager(requesterRole);
                    boolean isAssignee = task.getAssigneeIdsList() != null && task.getAssigneeIdsList().contains(requesterId);

                    if (!isAdminOrManager && !isAssignee) {
                        log.warn("Access Denied: User {} ({}) attempted to access task {}", requesterId, requesterRole, id);
                        return Mono.error(new AccessDeniedException("You are not authorized to view this task."));
                    }
                    
                    log.debug("Access granted for user {} to view task {}", requesterId, id);
                    return Mono.just(TaskResponseDTO.fromEntity(task));
                })
                .doOnError(ex -> log.error("Error fetching task {}", id, ex));
    }

    @Override
    public Flux<TaskResponseDTO> getAllTasks(String requesterId, String requesterRole, String authorizationHeader) { // Added header
        log.info("Fetching all tasks by User: {}, Role: {}", requesterId, requesterRole);

        if (!isAdmin(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to list all tasks.", requesterId, requesterRole);
            return Flux.error(new AccessDeniedException("Only ADMIN users can list all tasks."));
        }

        return taskRepository.findAll()
                .map(task -> {
                    task.deserializeLists();
                    return TaskResponseDTO.fromEntity(task);
                })
                .doOnError(ex -> log.error("Error fetching all tasks", ex));
    }

    @Override
    public Flux<TaskResponseDTO> getTasksByProjectId(String projectId, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Fetching tasks for projectId: {} by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        Mono<Void> authCheck = projectServiceClient.getProjectById(projectId, requesterId, requesterRole, authorizationHeader)
            .doOnError(e -> log.warn("Access Denied: User {} cannot access project {} tasks.", requesterId, projectId));

        return authCheck
                .thenMany(taskRepository.findByProjectId(projectId)
                    .map(task -> {
                        task.deserializeLists();
                        return TaskResponseDTO.fromEntity(task);
                    })
                )
                .doOnError(ex -> log.error("Error fetching tasks for projectId {}", projectId, ex));
    }


    // -------------------- UPDATE --------------------
    @Override
    public Mono<TaskResponseDTO> updateTask(String id, TaskRequestDTO request, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Updating task ID: {} by User: {}, Role: {}", id, requesterId, requesterRole);

        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
             log.warn("Access Denied: User {} ({}) attempted to update task {}", requesterId, requesterRole, id);
             return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can update task details."));
        }

        return validateProjectAndAssignees(request.getProjectId(), request.getAssigneeIdsList(), requesterId, requesterRole, authorizationHeader)
                .then(taskRepository.findById(id)
                        .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                        .flatMap(existing -> {
                            String oldStatus = existing.getStatus();
                            existing.setTitle(request.getTitle());
                            existing.setDescription(request.getDescription());
                            existing.setStatus(request.getStatus());
                            existing.setPriority(request.getPriority());
                            existing.setProjectId(request.getProjectId());
                            existing.setAssigneeIdsList(request.getAssigneeIdsList());
                            existing.setTagsList(request.getTagsList());
                            existing.setDueDate(request.getDueDate());
                            existing.setNew(false);
                            
                            existing.serializeLists(); // FIX for NULL columns

                            return taskRepository.save(existing)
                                    .flatMap(updated -> {
                                        updated.deserializeLists();
                                        Map<String, Object> payload = new HashMap<>();
                                        payload.put("taskId", updated.getId());
                                        payload.put("projectId", updated.getProjectId());
                                        payload.put("title", updated.getTitle());
                                        if (!oldStatus.equals(updated.getStatus())) {
                                            payload.put("message", "Task Status Changed to: " + updated.getStatus());
                                            payload.put("eventType", "TASK_STATUS_CHANGED");
                                            payload.put("payload", Map.of("from", oldStatus, "to", updated.getStatus()));
                                        } else {
                                            payload.put("message", "Task Updated: " + updated.getTitle());
                                            payload.put("eventType", "TASK_UPDATED");
                                        }
                                        // --- PASS AUTH HEADERS ---
                                        Mono<Void> notificationMono = sendNotificationsToAssignees(updated.getAssigneeIdsList(), payload, authorizationHeader, requesterId, requesterRole);
                                        
                                        return notificationMono
                                                .doOnSuccess(v -> publishEvent("TASK_UPDATED:" + updated.getId() + ":project:" + updated.getProjectId()))
                                                .thenReturn(TaskResponseDTO.fromEntity(updated));
                                    });
                        })
                        .doOnError(ex -> log.error("Error updating task {}", id, ex))
                );
    }

    // -------------------- STATUS UPDATE --------------------
    @Override
    public Mono<TaskResponseDTO> updateTaskStatus(String id, String status, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Updating status of task {} to {} by User: {}, Role: {}", id, status, requesterId, requesterRole);
        
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> {
                    
                    task.deserializeLists(); // FIX for auth check
                    
                    boolean isAdminOrManager = isAdmin(requesterRole) || isManager(requesterRole);
                    boolean isAssignee = task.getAssigneeIdsList() != null && task.getAssigneeIdsList().contains(requesterId);
                    
                    if (!isAdminOrManager && !isAssignee) {
                        log.warn("Access Denied: User {} ({}) attempted to update status on task {}", requesterId, requesterRole, id);
                        return Mono.error(new AccessDeniedException("Only Admins, Managers, or the task Assignee can update the status."));
                    }
                    
                    log.info("Access granted. User {} updating status for task {}", requesterId, id);
                    String oldStatus = task.getStatus();
                    task.setStatus(status);
                    task.setNew(false);
                    
                    return taskRepository.save(task)
                            .flatMap(updated -> {
                                updated.deserializeLists();
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("taskId", updated.getId());
                                payload.put("projectId", updated.getProjectId());
                                payload.put("title", updated.getTitle());
                                payload.put("message", "Task Status Updated to: " + updated.getStatus() + " by " + requesterId);
                                payload.put("eventType", "TASK_STATUS_CHANGED");
                                payload.put("payload", Map.of("from", oldStatus, "to", updated.getStatus(), "changedBy", requesterId));
                                // --- PASS AUTH HEADERS ---
                                Mono<Void> notificationMono = sendNotificationsToAssignees(updated.getAssigneeIdsList(), payload, authorizationHeader, requesterId, requesterRole);
                                return notificationMono
                                        .doOnSuccess(v -> publishEvent("TASK_STATUS_CHANGED:" + updated.getId() + ":project:" + updated.getProjectId()))
                                        .thenReturn(TaskResponseDTO.fromEntity(updated));
                            });
                });
    }

    // -------------------- DELETE --------------------
    @Override
    public Mono<Void> deleteTask(String id, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to delete task ID: {} by User: {}, Role: {}", id, requesterId, requesterRole);

        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
             log.warn("Access Denied: User {} ({}) attempted to delete task {}", requesterId, requesterRole, id);
             return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can delete tasks."));
        }

        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> {
                    task.deserializeLists();
                    List<String> assignees = task.getAssigneeIdsList() != null ? task.getAssigneeIdsList() : Collections.emptyList();
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Task Deleted: " + task.getTitle());
                    payload.put("taskId", id);
                    payload.put("eventType", "TASK_DELETED");
                    payload.put("title", task.getTitle());
                    payload.put("projectId", task.getProjectId());
                    // --- PASS AUTH HEADERS ---
                    Mono<Void> notificationMono = sendNotificationsToAssignees(assignees, payload, authorizationHeader, requesterId, requesterRole);
                    
                    return notificationMono
                            .then(taskRepository.delete(task))
                            .doOnSuccess(unused -> publishEvent("TASK_DELETED:" + id + ":project:" + task.getProjectId()));
                });
    }

    // -------------------- DELETE BY PROJECT --------------------
    @Override
    public Mono<Void> deleteTasksByProjectId(String projectId, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to delete all tasks for projectId: {} by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        if (!isAdmin(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to delete all tasks for project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN users can batch delete tasks by project."));
        }

        return taskRepository.findByProjectId(projectId)
                .flatMapSequential(task -> {
                    task.deserializeLists();
                    List<String> assignees = task.getAssigneeIdsList() != null ? task.getAssigneeIdsList() : Collections.emptyList();
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Task Deleted (Project Cleanup): " + task.getTitle());
                    payload.put("taskId", task.getId());
                    payload.put("eventType", "TASK_DELETED");
                    payload.put("title", task.getTitle());
                    payload.put("projectId", projectId);
                    // --- PASS AUTH HEADERS ---
                    Mono<Void> notificationMono = sendNotificationsToAssignees(assignees, payload, authorizationHeader, requesterId, requesterRole);
                    
                    return notificationMono
                            .then(taskRepository.delete(task))
                            .doOnSuccess(unused -> publishEvent("TASK_DELETED:" + task.getId() + ":project:" + task.getProjectId()));
                })
                .then();
    }
    
    // -------------------- ASSIGNEE MANAGEMENT --------------------
    @Override
    public Mono<TaskResponseDTO> addAssignees(String taskId, TaskAssigneesDTO dto, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to add assignees to task {} by User: {}, Role: {}", taskId, requesterId, requesterRole);
        
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
             log.warn("Access Denied: User {} ({}) attempted to add assignees to task {}", requesterId, requesterRole, taskId);
             return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can add assignees."));
        }

        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.deserializeLists();
                    List<String> newAssignees = dto.getAssigneeIdsList().stream()
                        .filter(id -> !task.getAssigneeIdsList().contains(id))
                        .toList();
                    
                    task.addAssignees(dto.getAssigneeIdsList()); // This calls serializeLists()
                    
                    return validateAssignees(task.getAssigneeIdsList(), requesterId, requesterRole, authorizationHeader)
                            .then(taskRepository.save(task))
                            .flatMap(saved -> {
                                saved.deserializeLists();
                                TaskResponseDTO t = TaskResponseDTO.fromEntity(saved);
                                Mono<Void> notificationMono = Mono.empty();
                                if (!newAssignees.isEmpty()) {
                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("message", "You were assigned to task: " + t.getTitle());
                                    payload.put("taskId", taskId);
                                    payload.put("eventType", "TASK_ASSIGNED");
                                    payload.put("title", t.getTitle());
                                    payload.put("projectId", t.getProjectId());
                                    // --- PASS AUTH HEADERS ---
                                    notificationMono = sendNotificationsToAssignees(newAssignees, payload, authorizationHeader, requesterId, requesterRole);
                                }
                                return notificationMono
                                        .doOnSuccess(v -> publishEvent("ASSIGNEES_ADDED:" + taskId + ":project:" + t.getProjectId()))
                                        .thenReturn(t);
                            });
                });
    }

    @Override
    public Mono<TaskResponseDTO> removeAssignees(String taskId, TaskAssigneesDTO dto, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to remove assignees from task {} by User: {}, Role: {}", taskId, requesterId, requesterRole);
        
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
             log.warn("Access Denied: User {} ({}) attempted to remove assignees from task {}", requesterId, requesterRole, taskId);
             return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can remove assignees."));
        }

        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.deserializeLists();
                    task.removeAssignees(dto.getAssigneeIdsList()); // This calls serializeLists()
                    
                    return taskRepository.save(task)
                            .flatMap(saved -> {
                                saved.deserializeLists();
                                TaskResponseDTO t = TaskResponseDTO.fromEntity(saved);
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("message", "You were removed from task: " + t.getTitle());
                                payload.put("taskId", taskId);
                                payload.put("eventType", "ASSIGNEES_REMOVED");
                                payload.put("title", t.getTitle());
                                payload.put("projectId", t.getProjectId());
                                // --- PASS AUTH HEADERS ---
                                Mono<Void> notificationMono = sendNotificationsToAssignees(dto.getAssigneeIdsList(), payload, authorizationHeader, requesterId, requesterRole);
                                return notificationMono
                                        .doOnSuccess(v -> publishEvent("ASSIGNEES_REMOVED:" + taskId + ":project:" + t.getProjectId()))
                                        .thenReturn(t);
                            });
                });
    }

    // -------------------- TAG MANAGEMENT --------------------
    @Override
    public Mono<TaskResponseDTO> addTags(String taskId, TaskTagsDTO dto, String requesterId, String requesterRole) {
        log.info("Attempting to add tags to task {} by User: {}, Role: {}", taskId, requesterId, requesterRole);
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
             log.warn("Access Denied: User {} ({}) attempted to add tags to task {}", requesterId, requesterRole, taskId);
             return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can add tags."));
        }
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.deserializeLists();
                    task.addTags(dto.getTagsList());
                    return taskRepository.save(task)
                            .map(saved -> {
                                saved.deserializeLists();
                                return TaskResponseDTO.fromEntity(saved);
                            })
                            .doOnSuccess(t -> publishEvent("TAGS_ADDED:" + taskId + ":project:" + t.getProjectId()));
                });
    }

    @Override
    public Mono<TaskResponseDTO> removeTags(String taskId, TaskTagsDTO dto, String requesterId, String requesterRole) {
        log.info("Attempting to remove tags from task {} by User: {}, Role: {}", taskId, requesterId, requesterRole);
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
             log.warn("Access Denied: User {} ({}) attempted to remove tags from task {}", requesterId, requesterRole, taskId);
             return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can remove tags."));
        }
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.deserializeLists();
                    task.removeTags(dto.getTagsList());
                    return taskRepository.save(task)
                            .map(saved -> {
                                saved.deserializeLists();
                                return TaskResponseDTO.fromEntity(saved);
                            })
                            .doOnSuccess(t -> publishEvent("TAGS_REMOVED:" + taskId + ":project:" + t.getProjectId()));
                });
    }
    
    // -------------------- TASK EVENT STREAM (IMPLEMENTATION) --------------------
    @Override
    public Flux<String> taskEventsStream() {
        return taskEventSink.asFlux();
    }


    // -------------------- VALIDATION HELPERS (MODIFIED) --------------------
    private Mono<Void> validateProjectAndAssignees(String projectId, List<String> assigneeIds, String requesterId, String requesterRole, String authorizationHeader) {
        // Chain project validation, then assignee validation
        return projectServiceClient.getProjectById(projectId, requesterId, requesterRole, authorizationHeader)
                .then(validateAssignees(assigneeIds, requesterId, requesterRole, authorizationHeader));
    }

    private Mono<Void> validateAssignees(List<String> assigneeIds, String requesterId, String requesterRole, String authorizationHeader) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return Mono.empty();
        
        return Flux.fromIterable(assigneeIds)
                .flatMap(userId -> userServiceClient.getUserById(userId, requesterId, requesterRole, authorizationHeader)
                        // --- THIS IS THE BUG FIX ---
                        // The WebClient call *already* returns Mono.error on 404 or 403.
                        // We remove the .switchIfEmpty that was causing the false error.
                )
                .then(); // Collect all Mono<Void> and return a single Mono<Void> when all are complete
    }
}