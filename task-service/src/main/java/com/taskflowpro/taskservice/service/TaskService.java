package com.taskflowpro.taskservice.service;

import com.taskflowpro.taskservice.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TaskService {

    // Add authHeader
    Mono<TaskResponseDTO> createTask(TaskRequestDTO request, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<TaskResponseDTO> getTaskById(String id, String requesterId, String requesterRole, String authorizationHeader);

    Flux<TaskResponseDTO> getAllTasks(String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Flux<TaskResponseDTO> getTasksByProjectId(String projectId, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<TaskResponseDTO> updateTask(String id, TaskRequestDTO request, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<TaskResponseDTO> updateTaskStatus(String id, String status, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<Void> deleteTask(String id, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<Void> deleteTasksByProjectId(String projectId, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<TaskResponseDTO> addAssignees(String taskId, TaskAssigneesDTO dto, String requesterId, String requesterRole, String authorizationHeader);

    // Add authHeader
    Mono<TaskResponseDTO> removeAssignees(String taskId, TaskAssigneesDTO dto, String requesterId, String requesterRole, String authorizationHeader);

    Mono<TaskResponseDTO> addTags(String taskId, TaskTagsDTO dto, String requesterId, String requesterRole);

    Mono<TaskResponseDTO> removeTags(String taskId, TaskTagsDTO dto, String requesterId, String requesterRole);

    Flux<String> taskEventsStream();
}