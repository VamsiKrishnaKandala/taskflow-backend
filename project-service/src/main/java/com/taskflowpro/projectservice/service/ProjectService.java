package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.dto.ProjectRequestDTO;
import com.taskflowpro.projectservice.dto.ProjectResponseDTO;
import com.taskflowpro.projectservice.dto.ProjectMembersDTO;
import com.taskflowpro.projectservice.dto.ProjectTagsDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for Project operations.
 * Provides abstraction for CRUD + member/tag management + event streaming.
 */
public interface ProjectService {

    Mono<ProjectResponseDTO> createProject(ProjectRequestDTO projectRequest);

    Mono<ProjectResponseDTO> getProjectById(String projectId);

    Flux<ProjectResponseDTO> getAllProjects();

    Mono<ProjectResponseDTO> updateProject(String projectId, ProjectRequestDTO projectRequest);

    Mono<Void> deleteProject(String projectId);

    Mono<ProjectResponseDTO> addMembers(String projectId, ProjectMembersDTO membersDTO);

    Mono<ProjectResponseDTO> removeMembers(String projectId, ProjectMembersDTO membersDTO);

    Mono<ProjectResponseDTO> addTags(String projectId, ProjectTagsDTO tagsDTO);

    Mono<ProjectResponseDTO> removeTags(String projectId, ProjectTagsDTO tagsDTO);

    Flux<String> projectEventsStream();
}