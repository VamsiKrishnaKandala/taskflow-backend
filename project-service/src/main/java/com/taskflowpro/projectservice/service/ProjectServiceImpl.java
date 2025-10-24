package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.client.TaskServiceClientWebClient;
import com.taskflowpro.projectservice.dto.*;
import com.taskflowpro.projectservice.exception.InvalidProjectDataException;
import com.taskflowpro.projectservice.exception.ProjectNotFoundException;
import com.taskflowpro.projectservice.model.Project;
import com.taskflowpro.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Project Service Implementation using WebClient instead of Feign.
 * Fully reactive and supports CRUD, member management, tag management,
 * and optional cascading task deletion via TaskService WebClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskServiceClientWebClient taskServiceClient; // WebClient-based Task Service
    private final Sinks.Many<String> projectEventSink = Sinks.many().multicast().onBackpressureBuffer();

    private void publishEvent(String event) {
        Sinks.EmitResult result = projectEventSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.error("Failed to publish event '{}': {}", event, result);
        } else {
            log.info("Event published: {}", event);
        }
    }

    // -------------------- CREATE --------------------
    @Override
    public Mono<ProjectResponseDTO> createProject(ProjectRequestDTO projectRequest) {
        log.info("Creating new project: {}", projectRequest.getName());

        if (projectRequest.getName() == null || projectRequest.getName().isBlank()) {
            return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
        }

        return projectRepository.findAll()
                .map(Project::getId)
                .filter(id -> id != null && id.startsWith("PF-"))
                .map(id -> Integer.parseInt(id.substring(3)))
                .sort()
                .last(0)
                .flatMap(max -> {
                    String nextId = String.format("PF-%03d", max + 1);
                    Project project = Project.builder()
                            .id(nextId)
                            .name(projectRequest.getName())
                            .description(projectRequest.getDescription())
                            .deadline(projectRequest.getDeadline())
                            .memberIdsList(projectRequest.getMemberIds())
                            .tagsList(projectRequest.getTags())
                            .isNew(true)
                            .build();

                    project.serializeLists();
                    log.info("Generated new project ID: {}", nextId);

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                return mapToDTO(saved);
                            })
                            .doOnSuccess(saved -> log.info("Project created successfully with ID: {}", saved.getId()))
                            .doOnError(error -> log.error("Error creating project: {}", error.getMessage()));
                });
    }

    // -------------------- READ --------------------
    @Override
    public Mono<ProjectResponseDTO> getProjectById(String projectId) {
        log.info("Fetching project with ID: {}", projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .map(p -> {
                    p.deserializeLists();
                    return mapToDTO(p);
                })
                .doOnNext(p -> log.info("Project retrieved successfully: {}", p.getName()))
                .doOnError(e -> log.error("Error fetching project: {}", e.getMessage()));
    }

    @Override
    public Flux<ProjectResponseDTO> getAllProjects() {
        log.info("Fetching all projects...");
        return projectRepository.findAll()
                .map(p -> {
                    p.deserializeLists();
                    return mapToDTO(p);
                })
                .doOnComplete(() -> log.info("Fetched all projects successfully"))
                .doOnError(e -> log.error("Error fetching projects: {}", e.getMessage()));
    }

    // -------------------- UPDATE --------------------
    @Override
    public Mono<ProjectResponseDTO> updateProject(String projectId, ProjectRequestDTO projectRequest) {
        log.info("Updating project with ID: {}", projectId);

        if (projectRequest.getName() == null || projectRequest.getName().isBlank()) {
            return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
        }

        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(existing -> {
                    existing.setName(projectRequest.getName());
                    existing.setDescription(projectRequest.getDescription());
                    existing.setDeadline(projectRequest.getDeadline());
                    existing.setMemberIdsList(projectRequest.getMemberIds());
                    existing.setTagsList(projectRequest.getTags());
                    existing.serializeLists();

                    return projectRepository.save(existing)
                            .map(p -> {
                                p.deserializeLists();
                                return mapToDTO(p);
                            });
                })
                .doOnSuccess(p -> log.info("Project updated successfully: {}", p.getId()))
                .doOnError(e -> log.error("Error updating project: {}", e.getMessage()));
    }

    // -------------------- DELETE --------------------
    @Override
    public Mono<Void> deleteProject(String projectId) {
        log.info("Deleting project with ID: {}", projectId);

        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(existing -> 
                    taskServiceClient.deleteTasksByProjectId(projectId) // Cascade delete tasks
                        .then(projectRepository.delete(existing))
                        .doOnSuccess(v -> log.info("Project deleted successfully: {}", projectId))
                        .doOnError(e -> log.error("Error deleting project {}: {}", projectId, e.getMessage()))
                );
    }

    // -------------------- MEMBER MANAGEMENT --------------------
    @Override
    public Mono<ProjectResponseDTO> addMembers(String projectId, ProjectMembersDTO membersDTO) {
        log.info("Adding members {} to project {}", membersDTO.getMemberIds(), projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                    project.deserializeLists();
                    Set<String> currentMembers = new HashSet<>(project.getMemberIdsList());
                    currentMembers.addAll(membersDTO.getMemberIds());
                    project.setMemberIdsList(List.copyOf(currentMembers));
                    project.serializeLists();

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                membersDTO.getMemberIds().forEach(id -> publishEvent("project.member.added: " + id + " -> " + projectId));
                                return mapToDTO(saved);
                            });
                });
    }

    @Override
    public Mono<ProjectResponseDTO> removeMembers(String projectId, ProjectMembersDTO membersDTO) {
        log.info("Removing members {} from project {}", membersDTO.getMemberIds(), projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                    project.deserializeLists();
                    Set<String> currentMembers = new HashSet<>(project.getMemberIdsList());
                    currentMembers.removeAll(membersDTO.getMemberIds());
                    project.setMemberIdsList(List.copyOf(currentMembers));
                    project.serializeLists();

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                membersDTO.getMemberIds().forEach(id -> publishEvent("project.member.removed: " + id + " -> " + projectId));
                                return mapToDTO(saved);
                            });
                });
    }

    // -------------------- TAG MANAGEMENT --------------------
    @Override
    public Mono<ProjectResponseDTO> addTags(String projectId, ProjectTagsDTO tagsDTO) {
        log.info("Adding tags {} to project {}", tagsDTO.getTags(), projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                    project.deserializeLists();
                    Set<String> currentTags = new HashSet<>(project.getTagsList());
                    currentTags.addAll(tagsDTO.getTags());
                    project.setTagsList(List.copyOf(currentTags));
                    project.serializeLists();

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                tagsDTO.getTags().forEach(tag -> publishEvent("project.tag.added: " + tag + " -> " + projectId));
                                return mapToDTO(saved);
                            });
                });
    }

    @Override
    public Mono<ProjectResponseDTO> removeTags(String projectId, ProjectTagsDTO tagsDTO) {
        log.info("Removing tags {} from project {}", tagsDTO.getTags(), projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                    project.deserializeLists();
                    Set<String> currentTags = new HashSet<>(project.getTagsList());
                    currentTags.removeAll(tagsDTO.getTags());
                    project.setTagsList(List.copyOf(currentTags));
                    project.serializeLists();

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                tagsDTO.getTags().forEach(tag -> publishEvent("project.tag.removed: " + tag + " -> " + projectId));
                                return mapToDTO(saved);
                            });
                });
    }

    // -------------------- EVENT STREAM --------------------
    @Override
    public Flux<String> projectEventsStream() {
        return projectEventSink.asFlux();
    }

    // -------------------- HELPER METHODS --------------------
    private ProjectResponseDTO mapToDTO(Project project) {
        return ProjectResponseDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .deadline(project.getDeadline())
                .memberIds(project.getMemberIdsList())
                .tags(project.getTagsList())
                .build();
    }
}
