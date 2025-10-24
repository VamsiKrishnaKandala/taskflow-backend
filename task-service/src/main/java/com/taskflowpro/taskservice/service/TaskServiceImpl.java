package com.taskflowpro.taskservice.service;

import com.taskflowpro.taskservice.client.ProjectServiceClientWebClient;
import com.taskflowpro.taskservice.client.UserServiceClientWebClient;
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
import java.util.List;

/**
 * Fully reactive TaskService implementation using WebClient for Project & User checks.
 * Handles CRUD, assignee & tag management, status updates, and task events streaming.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectServiceClientWebClient projectServiceClient;
    private final UserServiceClientWebClient userServiceClient;
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
                        .last(0) // get max existing ID
                )
                .defaultIfEmpty(0) // if no tasks exist yet
                .flatMap(max -> {
                    String nextId = String.format("TF-%03d", max + 1); // generate next ID
                    Task task = Task.builder()
                            .id(nextId) // <--- assign generated ID
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
                            .map(TaskResponseDTO::fromEntity)
                            .doOnSuccess(saved -> {
                                publishEvent("TASK_CREATED:" + saved.getId());
                                log.info("Task created successfully with ID: {}", saved.getId());
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
                            existing.setTitle(request.getTitle());
                            existing.setDescription(request.getDescription());
                            existing.setStatus(request.getStatus());
                            existing.setPriority(request.getPriority());
                            existing.setProjectId(request.getProjectId());
                            existing.setAssigneeIdsList(request.getAssigneeIdsList());
                            existing.setTagsList(request.getTagsList());
                            existing.setDueDate(request.getDueDate());
                            existing.setNew(false);
                            return taskRepository.save(existing);
                        })
                        .map(TaskResponseDTO::fromEntity)
                        .doOnSuccess(updated -> publishEvent("TASK_UPDATED:" + updated.getId()))
                        .doOnError(ex -> log.error("Error updating task {}", id, ex))
                );
    }

    // -------------------- DELETE --------------------
    @Override
    public Mono<Void> deleteTask(String id) {
        log.info("Deleting task with ID: {}", id);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> taskRepository.delete(task)
                        .doOnSuccess(unused -> publishEvent("TASK_DELETED:" + id))
                        .doOnError(ex -> log.error("Error deleting task {}", id, ex))
                );
    }

    // -------------------- STATUS --------------------
    @Override
    public Mono<TaskResponseDTO> updateTaskStatus(String id, String status, String updatedBy) {
        log.info("Updating status of task {} to {}", id, status);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + id)))
                .flatMap(task -> {
                    task.setStatus(status);
                    return taskRepository.save(task);
                })
                .map(TaskResponseDTO::fromEntity)
                .doOnSuccess(updated -> publishEvent("TASK_STATUS_UPDATED:" + updated.getId()));
    }

    // -------------------- ASSIGNEE MANAGEMENT --------------------
    @Override
    public Mono<TaskResponseDTO> addAssignees(String taskId, TaskAssigneesDTO dto) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    List<String> updatedAssignees = task.addAssignees(dto.getAssigneeIdsList());
                    return validateAssignees(updatedAssignees)
                            .then(taskRepository.save(task))
                            .map(TaskResponseDTO::fromEntity)
                            .doOnSuccess(t -> publishEvent("ASSIGNEES_ADDED:" + taskId));
                });
    }

    @Override
    public Mono<TaskResponseDTO> removeAssignees(String taskId, TaskAssigneesDTO dto) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException("Task not found with ID: " + taskId)))
                .flatMap(task -> {
                    task.removeAssignees(dto.getAssigneeIdsList());
                    return taskRepository.save(task)
                            .map(TaskResponseDTO::fromEntity)
                            .doOnSuccess(t -> publishEvent("ASSIGNEES_REMOVED:" + taskId));
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
