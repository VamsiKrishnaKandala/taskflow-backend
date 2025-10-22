package com.taskflowpro.projectservice.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;

    // -------------------- Event Sink --------------------
    // Reactive sink to publish project-related events
    private final Sinks.Many<String> projectEventSink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Publishes a project-related event to the reactive event stream.
     * @param event String describing the event (e.g., "project.member.added: userId -> projectId")
     */
    private void publishEvent(String event) {
        Sinks.EmitResult result = projectEventSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.error("Failed to publish event '{}': {}", event, result);
        } else {
            log.info("Event published: {}", event);
        }
    }

    /**
     * Creates a new Project with PF-XXX custom ID.
     */
    public Mono<Project> createProject(Project project) {
        log.info("Creating new project: {}", project.getName());

        if (project.getName() == null || project.getName().isBlank()) {
            log.warn("Project name is invalid or missing");
            return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
        }

        // Get the max ID number reactively
        return projectRepository.findAll()
                .map(Project::getId)
                .filter(id -> id != null && id.startsWith("PF-"))
                .map(id -> id.substring(3))
                .map(Integer::parseInt)
                .sort()
                .last(0)  // get max, default 0
                .flatMap(max -> {
                    String nextId = String.format("PF-%03d", max + 1);
                    project.setId(nextId);
                    project.setNew(true); // ✅ Important fix
                    project.serializeLists();

                    log.info("Generated new project ID: {}", nextId);

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                return saved;
                            })
                            .doOnSuccess(saved -> log.info("Project created successfully with ID: {}", saved.getId()))
                            .doOnError(error -> log.error("Error creating project: {}", error.getMessage()));
                });
    }

    /**
     * Fetches a single project by ID, throws custom exception if not found.
     */
    public Mono<Project> getProjectById(String id) {
        log.info("Fetching project with ID: {}", id);
        return projectRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(id)))
                .map(p -> {
                    p.deserializeLists();
                    return p;
                })
                .doOnNext(p -> log.info("Project retrieved successfully: {}", p.getName()))
                .doOnError(e -> log.error("Error fetching project: {}", e.getMessage()));
    }

    /**
     * Fetches all projects.
     */
    public Flux<Project> getAllProjects() {
        log.info("Fetching all projects...");
        return projectRepository.findAll()
                .map(p -> {
                    p.deserializeLists();
                    return p;
                })
                .doOnComplete(() -> log.info("Fetched all projects successfully"))
                .doOnError(e -> log.error("Error fetching projects: {}", e.getMessage()));
    }

    /**
     * Updates an existing project by ID, or throws if not found.
     */
    public Mono<Project> updateProject(String id, Project updatedProject) {
        log.info("Updating project with ID: {}", id);

        if (updatedProject.getName() == null || updatedProject.getName().isBlank()) {
            return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
        }

        return projectRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(id)))
                .flatMap(existing -> {
                    existing.setName(updatedProject.getName());
                    existing.setDescription(updatedProject.getDescription());
                    existing.setDeadline(updatedProject.getDeadline());
                    existing.setMemberIdsList(updatedProject.getMemberIdsList());
                    existing.setTagsList(updatedProject.getTagsList());
                    existing.serializeLists();

                    return projectRepository.save(existing)
                            .map(p -> {
                                p.deserializeLists();
                                return p;
                            });
                })
                .doOnSuccess(p -> log.info("Project updated successfully: {}", p.getId()))
                .doOnError(e -> log.error("Error updating project: {}", e.getMessage()));
    }

    /**
     * Deletes a project by ID, throws ProjectNotFoundException if it doesn't exist.
     */
    public Mono<Void> deleteProject(String id) {
        log.info("Deleting project with ID: {}", id);

        return projectRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(id)))
                .flatMap(existing -> projectRepository.delete(existing)
                        .doOnSuccess(v -> log.info("Project deleted successfully: {}", id))
                        .doOnError(e -> log.error("Error deleting project {}: {}", id, e.getMessage()))
                );
    }

    // -------------------- New Methods for Team Management --------------------

    public Mono<Project> addMembers(String projectId, List<String> newMemberIds) {
        log.info("Adding members {} to project {}", newMemberIds, projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                	project.deserializeLists();
                	
                    Set<String> currentMembers = new HashSet<>(project.getMemberIdsList());
                    currentMembers.addAll(newMemberIds);
                    project.setMemberIdsList(List.copyOf(currentMembers));
                    project.serializeLists();
                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                newMemberIds.forEach(id -> publishEvent("project.member.added: " + id + " -> " + projectId));
                                return saved;
                            });
                });
    }

    public Mono<Project> removeMembers(String projectId, List<String> removeMemberIds) {
        log.info("Removing members {} from project {}", removeMemberIds, projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                	project.deserializeLists();
                	
                    Set<String> currentMembers = new HashSet<>(project.getMemberIdsList());
                    currentMembers.removeAll(removeMemberIds);
                    project.setMemberIdsList(List.copyOf(currentMembers));
                    project.serializeLists();
                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                removeMemberIds.forEach(id -> publishEvent("project.member.removed: " + id + " -> " + projectId));
                                return saved;
                            });
                });
    }

    // -------------------- New Methods for Tag Management --------------------

    public Mono<Project> addTags(String projectId, List<String> tags) {
        log.info("Adding tags {} to project {}", tags, projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                	project.deserializeLists();
                	
                    Set<String> currentTags = new HashSet<>(project.getTagsList());
                    currentTags.addAll(tags);
                    project.setTagsList(List.copyOf(currentTags));
                    project.serializeLists();
                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                tags.forEach(tag -> publishEvent("project.tag.added: " + tag + " -> " + projectId));
                                return saved;
                            });
                });
    }

    public Mono<Project> removeTags(String projectId, List<String> tags) {
        log.info("Removing tags {} from project {}", tags, projectId);
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                	project.deserializeLists();
                	
                    Set<String> currentTags = new HashSet<>(project.getTagsList());
                    currentTags.removeAll(tags);
                    project.setTagsList(List.copyOf(currentTags));
                    project.serializeLists();
                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                tags.forEach(tag -> publishEvent("project.tag.removed: " + tag + " -> " + projectId));
                                return saved;
                            });
                });
    }

    // -------------------- Event Stream --------------------

    /**
     * Exposes a reactive Flux stream for all project events.
     */
    public Flux<String> projectEventsStream() {
        return projectEventSink.asFlux();
    }

}