package com.taskflowpro.taskservice.service;

import com.taskflowpro.taskservice.dto.TaskAssigneesDTO;
import com.taskflowpro.taskservice.dto.TaskRequestDTO;
import com.taskflowpro.taskservice.dto.TaskResponseDTO;
import com.taskflowpro.taskservice.dto.TaskTagsDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for Task business logic.
 * Defines all operations as reactive methods using DTOs.
 */
public interface TaskService {

    Mono<TaskResponseDTO> createTask(TaskRequestDTO taskDTO);

    Mono<TaskResponseDTO> getTaskById(String id);

    Flux<TaskResponseDTO> getAllTasks();

    Flux<TaskResponseDTO> getTasksByProjectId(String projectId);

    Mono<TaskResponseDTO> updateTask(String id, TaskRequestDTO updatedDTO);

    Mono<TaskResponseDTO> updateTaskStatus(String id, String newStatus, String changedBy);

    Mono<Void> deleteTask(String id);

    Mono<TaskResponseDTO> addAssignees(String taskId, TaskAssigneesDTO dto);

    Mono<TaskResponseDTO> removeAssignees(String taskId, TaskAssigneesDTO dto);

    Mono<TaskResponseDTO> addTags(String taskId, TaskTagsDTO dto);

    Mono<TaskResponseDTO> removeTags(String taskId, TaskTagsDTO dto);

    Flux<String> taskEventsStream();
}