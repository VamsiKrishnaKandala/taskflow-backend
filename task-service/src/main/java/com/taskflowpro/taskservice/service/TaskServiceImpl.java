package com.taskflowpro.taskservice.service;

import com.taskflowpro.taskservice.client.ProjectServiceClientWebClient;
import com.taskflowpro.taskservice.client.UserServiceClientWebClient;
import com.taskflowpro.taskservice.client.NotificationServiceClientWebClient;
import com.taskflowpro.taskservice.dto.*;
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

/**
 * Fully reactive TaskService implementation using WebClient for Project & User checks.
 * Handles CRUD, assignee & tag management, status updates, and task events streaming.
 * NOW INCLUDES SAFE, REACTIVE NOTIFICATION SENDING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectServiceClientWebClient projectServiceClient;
    private final UserServiceClientWebClient userServiceClient;
    private final NotificationServiceClientWebClient notificationClient;
    private final Sinks.Many<String> taskEventSink;

    // -------------------- EVENT PUBLISHER --------------------
    private void publishEvent(String event) {
        Sinks.EmitResult result = taskEventSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.error("Failed to publish task event '{}': {}", event, result);
        } else {
            log.info("Task event published: {}", event);
        }
    }

    // ✅ FIX 1: Helper method to send notifications safely, returning a Mono<Void>
    private Mono<Void> sendNotificationSafe(String recipientUserId, Map<String, Object> basePayload) {
        // Clone and customize payload for a single recipient
        Map<String, Object> payload = new HashMap<>(basePayload);
        payload.put("recipientUserId", recipientUserId);

        return notificationClient.sendNotificationEvent(payload)
                .doOnError(ex -> log.error("Notification to user {} failed: {}", recipientUserId, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty()) // Continue flow if notification fails
                .then(); // Convert Mono<NotificationResponseDTO> to Mono<Void>
    }
    
    // ✅ FIX 2: Creates and executes individual notification monos for a list of recipients
    private Mono<Void> sendNotificationsToAssignees(List<String> assigneeIds, Map<String, Object> basePayload) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return Mono.empty();
        }
        
        // Use Flux.fromIterable and flatMap to parallelize non-blocking notification sending
        return Flux.fromIterable(assigneeIds)
            .flatMap(id -> sendNotificationSafe(id, basePayload))
            .then(); // Convert Flux<Void> to Mono<Void>
    }

    // -------------------- CREATE --------------------
    @Override
    public Mono<TaskResponseDTO> createTask(TaskRequestDTO request) {
        log.info("Creating new task under projectId: {}", request.getProjectId());

        return validateProjectAndAssignees(request.getProjectId(), request.getAssigneeIdsList())
                .then(taskRepository.findAll()
                        .map(Task::getId)
                        .filter(id -> id != null && id.startsWith("TF-"))
                        .map(id -> Integer.parseInt(id.substring(3))) // extract numeric part
                        .sort()
                        .last(0)) // get max existing ID
                .defaultIfEmpty(0) // if no tasks exist yet
                .flatMap(max -> {
                    String nextId = String.format("TF-%03d", max + 1); // generate next ID
                    Task task = Task.builder()
                            .id(nextId)
                            .projectId(request.getProjectId())
                            .title(request.getTitle())
                            .description(request.getDescription())
                            .status(request.getStatus())
                            .priority(request.getPriority())
                            .assigneeIdsList(request.getAssigneeIdsList())
                            .tagsList(request.getTagsList())
                            .dueDate(request.getDueDate())
                            .createdBy(request.getCreatedBy())
                            .createdAt(LocalDateTime.now())
                            .isNew(true)
                            .build();

                    return taskRepository.save(task)
                            .flatMap(saved -> {
                                // 1. Build Base Notification Payload
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("message", "New Task Assigned: " + saved.getTitle());
                                payload.put("taskId", saved.getId());
                                payload.put("eventType", "TASK_CREATED");
                                payload.put("taskTitle", saved.getTitle());
                                payload.put("projectId", saved.getProjectId());
                                
                                // 2. Chain Notifications before DTO creation
                                Mono<Void> notificationMono = sendNotificationsToAssignees(saved.getAssigneeIdsList(), payload);
                                
                                // Wait for notification to finish, then map the saved result to the final DTO.
                                return notificationMono
                                        .doOnSuccess(v -> {
                                            publishEvent("TASK_CREATED:" + saved.getId());
                                            log.info("Task created successfully with ID: {}", saved.getId());
                                        })
                                        .thenReturn(TaskResponseDTO.fromEntity(saved)); // Use thenReturn to pass the DTO
                            })
                            .doOnError(ex -> log.error("Error creating task", ex));
                });
    }

    // -------------------- READ --------------------
    @Override
    public Mono<TaskResponseDTO> getTaskById(String id) {
        log.info("Fetching task with ID: {}", id);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .map(TaskResponseDTO::fromEntity)
                .doOnError(ex -> log.error("Error fetching task {}", id, ex));
    }

    @Override
    public Flux<TaskResponseDTO> getAllTasks() {
        log.info("Fetching all tasks");
        return taskRepository.findAll()
                .map(TaskResponseDTO::fromEntity)
                .doOnError(ex -> log.error("Error fetching all tasks", ex));
    }

    @Override
    public Flux<TaskResponseDTO> getTasksByProjectId(String projectId) {
        log.info("Fetching tasks for projectId: {}", projectId);
        return taskRepository.findByProjectId(projectId)
                .map(TaskResponseDTO::fromEntity)
                .doOnError(ex -> log.error("Error fetching tasks for projectId {}", projectId, ex));
    }

    // -------------------- UPDATE --------------------
    @Override
    public Mono<TaskResponseDTO> updateTask(String id, TaskRequestDTO request) {
        log.info("Updating task with ID: {}", id);
        return validateProjectAndAssignees(request.getProjectId(), request.getAssigneeIdsList())
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
                            
                            return taskRepository.save(existing)
                                    .flatMap(updated -> {
                                        // 1. Build Base Notification Payload
                                        Map<String, Object> payload = new HashMap<>();
                                        payload.put("taskId", updated.getId());
                                        payload.put("projectId", updated.getProjectId());
                                        payload.put("title", updated.getTitle());
                                        
                                        // Determine event type and message
                                        if (!oldStatus.equals(updated.getStatus())) {
                                            payload.put("message", "Task Status Changed to: " + updated.getStatus());
                                            payload.put("eventType", "TASK_STATUS_CHANGED");
                                            payload.put("payload", Map.of("from", oldStatus, "to", updated.getStatus()));
                                        } else {
                                            payload.put("message", "Task Updated: " + updated.getTitle());
                                            payload.put("eventType", "TASK_UPDATED");
                                        }

                                        // 2. Chain Notifications before DTO creation
                                        Mono<Void> notificationMono = sendNotificationsToAssignees(updated.getAssigneeIdsList(), payload);
                                        
                                        // Wait for notification, then return DTO
                                        return notificationMono
                                                .doOnSuccess(v -> publishEvent("TASK_UPDATED:" + updated.getId()))
                                                .thenReturn(TaskResponseDTO.fromEntity(updated));
                                    });
                        })
                        .doOnError(ex -> log.error("Error updating task {}", id, ex))
                );
    }

    // -------------------- DELETE --------------------
    @Override
    public Mono<Void> deleteTask(String id) {
        log.info("Deleting task with ID: {}", id);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> {
                    // 1. Build Base Notification Payload
                    List<String> assignees = task.getAssigneeIdsList() != null ? task.getAssigneeIdsList() : List.of();
                    
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Task Deleted: " + task.getTitle());
                    payload.put("taskId", id);
                    payload.put("eventType", "TASK_DELETED");
                    payload.put("title", task.getTitle());
                    payload.put("projectId", task.getProjectId());

                    // 2. Chain Notifications before Deletion
                    Mono<Void> notificationMono = sendNotificationsToAssignees(assignees, payload);
                    
                    return notificationMono
                            .then(taskRepository.delete(task))
                            .doOnSuccess(unused -> publishEvent("TASK_DELETED:" + id))
                            .doOnError(ex -> log.error("Error deleting task {}", id, ex));
                });
    }

    // -------------------- DELETE BY PROJECT --------------------
    @Override
    public Mono<Void> deleteTasksByProjectId(String projectId) {
        log.info("Deleting all tasks for projectId: {}", projectId);
        
        // Use flatMapSequential to process each task one by one, ensuring notification before deletion
        return taskRepository.findByProjectId(projectId)
                .flatMapSequential(task -> {
                    // 1. Build Base Notification Payload
                    List<String> assignees = task.getAssigneeIdsList() != null ? task.getAssigneeIdsList() : List.of();
                    
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Task Deleted: " + task.getTitle());
                    payload.put("taskId", task.getId());
                    payload.put("eventType", "TASK_DELETED");
                    payload.put("title", task.getTitle());
                    payload.put("projectId", projectId);

                    // 2. Chain Notification before Deletion and Event Publishing
                    Mono<Void> notificationMono = sendNotificationsToAssignees(assignees, payload);
                    
                    return notificationMono
                            .then(taskRepository.delete(task))
                            .doOnSuccess(unused -> publishEvent("TASK_DELETED:" + task.getId()))
                            .doOnError(ex -> log.error("Error deleting task {}", task.getId(), ex));
                })
                .then(); // Completes when the entire Flux (all deletions) is done
    }

    // -------------------- STATUS --------------------
    @Override
    public Mono<TaskResponseDTO> updateTaskStatus(String id, String status, String updatedBy) {
        log.info("Updating status of task {} to {}", id, status);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> {
                    String oldStatus = task.getStatus();
                    task.setStatus(status);
                    return taskRepository.save(task);
                })
                .flatMap(updated -> {
                    // 1. Prepare Notification Mono
                    Mono<Void> notificationMono = Mono.empty();
                    if (updated.getAssigneeIdsList() != null && !updated.getAssigneeIdsList().isEmpty()) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("taskId", updated.getId());
                        payload.put("projectId", updated.getProjectId());
                        payload.put("title", updated.getTitle());
                        
                        payload.put("message", "Task Status Updated to: " + updated.getStatus());
                        payload.put("eventType", "TASK_STATUS_CHANGED");
                        payload.put("payload", Map.of("to", updated.getStatus())); 

                        notificationMono = sendNotificationsToAssignees(updated.getAssigneeIdsList(), payload);
                    }
                    
                    // 2. Chain Notification and Event before returning the DTO
                    return notificationMono
                            .doOnSuccess(v -> publishEvent("TASK_STATUS_UPDATED:" + updated.getId()))
                            .thenReturn(TaskResponseDTO.fromEntity(updated));
                });
    }

    // -------------------- ASSIGNEE MANAGEMENT --------------------
    @Override
    public Mono<TaskResponseDTO> addAssignees(String taskId, TaskAssigneesDTO dto) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    List<String> newAssignees = task.addAssignees(dto.getAssigneeIdsList()); // Updates internal list
                    
                    // Filter to only notify newly added assignees
                    List<String> addedAssignees = dto.getAssigneeIdsList().stream()
                            .filter(id -> newAssignees.contains(id)) // Should already be covered by task.addAssignees logic
                            .toList();

                    return validateAssignees(newAssignees) // Validate the *full* new list
                            .then(taskRepository.save(task))
                            .flatMap(saved -> {
                                TaskResponseDTO t = TaskResponseDTO.fromEntity(saved);
                                
                                // 1. Prepare Notification Mono for *only* the added members
                                Mono<Void> notificationMono = Mono.empty();
                                if (!addedAssignees.isEmpty()) {
                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("message", "You were added to task: " + t.getTitle());
                                    payload.put("taskId", taskId);
                                    payload.put("eventType", "TASK_ASSIGNED");
                                    payload.put("title", t.getTitle());
                                    payload.put("projectId", t.getProjectId());

                                    notificationMono = sendNotificationsToAssignees(addedAssignees, payload);
                                }
                                
                                // 2. Chain Notification and Event before returning the DTO
                                return notificationMono
                                        .doOnSuccess(v -> publishEvent("ASSIGNEES_ADDED:" + taskId))
                                        .thenReturn(t);
                            });
                });
    }

    @Override
    public Mono<TaskResponseDTO> removeAssignees(String taskId, TaskAssigneesDTO dto) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.removeAssignees(dto.getAssigneeIdsList()); // Updates internal list
                    
                    return taskRepository.save(task)
                            .flatMap(saved -> {
                                TaskResponseDTO t = TaskResponseDTO.fromEntity(saved);
                                
                                // 1. Prepare Notification Mono for *only* the removed members
                                Mono<Void> notificationMono = Mono.empty();
                                if (!dto.getAssigneeIdsList().isEmpty()) {
                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("message", "You were removed from task: " + t.getTitle());
                                    payload.put("taskId", taskId);
                                    payload.put("eventType", "ASSIGNEES_REMOVED"); 
                                    payload.put("title", t.getTitle());
                                    payload.put("projectId", t.getProjectId());

                                    // Send notification to the list of members passed in the DTO
                                    notificationMono = sendNotificationsToAssignees(dto.getAssigneeIdsList(), payload);
                                }
                                
                                // 2. Chain Notification and Event before returning the DTO
                                return notificationMono
                                        .doOnSuccess(v -> publishEvent("ASSIGNEES_REMOVED:" + taskId))
                                        .thenReturn(t);
                            });
                });
    }

    // -------------------- TAG MANAGEMENT --------------------
    @Override
    public Mono<TaskResponseDTO> addTags(String taskId, TaskTagsDTO dto) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.addTags(dto.getTagsList());
                    return taskRepository.save(task)
                            .map(TaskResponseDTO::fromEntity)
                            .doOnSuccess(t -> publishEvent("TAGS_ADDED:" + taskId));
                });
    }

    @Override
    public Mono<TaskResponseDTO> removeTags(String taskId, TaskTagsDTO dto) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.removeTags(dto.getTagsList());
                    return taskRepository.save(task)
                            .map(TaskResponseDTO::fromEntity)
                            .doOnSuccess(t -> publishEvent("TAGS_REMOVED:" + taskId));
                });
    }

    // -------------------- TASK EVENT STREAM --------------------
    @Override
    public Flux<String> taskEventsStream() {
        return taskEventSink.asFlux();
    }

    // -------------------- VALIDATION HELPERS --------------------
    private Mono<Void> validateProjectAndAssignees(String projectId, List<String> assigneeIds) {
        // Just chain the assignee validation; project validation is already handled in the WebClient
        return projectServiceClient.getProjectById(projectId)
                .then(validateAssignees(assigneeIds));
    }

    private Mono<Void> validateAssignees(List<String> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return Mono.empty();
        return Flux.fromIterable(assigneeIds)
                .flatMap(userId -> userServiceClient.getUserById(userId)
                        .switchIfEmpty(Mono.error(new InvalidTaskDataException("Invalid Assignee ID: " + userId))))
                .then();
    }
}