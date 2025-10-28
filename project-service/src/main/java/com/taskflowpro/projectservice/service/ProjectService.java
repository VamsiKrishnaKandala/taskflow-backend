package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.dto.ProjectMembersDTO;
import com.taskflowpro.projectservice.dto.ProjectRequestDTO;
import com.taskflowpro.projectservice.dto.ProjectResponseDTO;
import com.taskflowpro.projectservice.dto.ProjectTagsDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface defining business logic for Project operations.
 * All methods now include authorization parameters.
 */
public interface ProjectService {

    /**
     * Creates a new project.
     * Access: ADMIN, MANAGER
     * (Makes downstream calls to NotificationService)
     */
    Mono<ProjectResponseDTO> createProject(ProjectRequestDTO projectRequest, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Retrieves a project by its ID.
     * Access: ADMIN, MANAGER, or Project Member
     * (Makes downstream call to UserService if enriching member data - future)
     */
    Mono<ProjectResponseDTO> getProjectById(String projectId, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Retrieves all projects, filtered by user role.
     * Access: ADMIN (all), MANAGER (their projects)
     */
    Flux<ProjectResponseDTO> getAllProjects(String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Updates an existing project.
     * Access: ADMIN, MANAGER (if project lead/owner)
     * (Makes downstream calls to NotificationService)
     */
    Mono<ProjectResponseDTO> updateProject(String projectId, ProjectRequestDTO projectRequest, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Deletes a project.
     * Access: ADMIN only
     * (Makes downstream calls to NotificationService and TaskService)
     */
    Mono<Void> deleteProject(String projectId, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Adds members to a project.
     * Access: ADMIN, MANAGER (if project lead/owner)
     * (Makes downstream calls to NotificationService)
     */
    Mono<ProjectResponseDTO> addMembers(String projectId, ProjectMembersDTO membersDTO, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Removes members from a project.
     * Access: ADMIN, MANAGER (if project lead/owner)
     * (Makes downstream calls to NotificationService)
     */
    Mono<ProjectResponseDTO> removeMembers(String projectId, ProjectMembersDTO membersDTO, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Adds tags to a project.
     * Access: ADMIN, MANAGER (if project lead/owner)
     */
    Mono<ProjectResponseDTO> addTags(String projectId, ProjectTagsDTO tagsDTO, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Removes tags from a project.
     * Access: ADMIN, MANAGER (if project lead/owner)
     */
    Mono<ProjectResponseDTO> removeTags(String projectId, ProjectTagsDTO tagsDTO, String requesterId, String requesterRole, String authorizationHeader);

    /**
     * Retrieves the event stream for projects.
     */
    Flux<String> projectEventsStream();
}