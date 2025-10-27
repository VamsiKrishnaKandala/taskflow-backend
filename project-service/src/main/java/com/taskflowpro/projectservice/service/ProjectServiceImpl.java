package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.client.TaskServiceClientWebClient;
import com.taskflowpro.projectservice.client.NotificationServiceClientWebClient;
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
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

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
    private final TaskServiceClientWebClient taskServiceClient;
    private final NotificationServiceClientWebClient notificationClient;
    private final Sinks.Many<String> projectEventSink = Sinks.many().multicast().onBackpressureBuffer();

    private void publishEvent(String event) {
        Sinks.EmitResult result = projectEventSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.error("Failed to publish event '{}': {}", event, result);
        } else {
            log.info("Event published: {}", event);
        }
    }

    // FIX 1: Returns Mono<Void> to allow chaining
    private Mono<Void> sendNotificationSafe(Map<String, Object> payload) {
        return notificationClient.sendNotificationEvent(payload)
                .doOnError(ex -> log.error("Notification failed: {}", ex.getMessage()))
                .onErrorResume(ex -> Mono.empty()) // Continue flow if notification fails
                .then(); // Convert Mono<NotificationResponseDTO> to Mono<Void>
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
                            .doOnSuccess(saved -> {
                                log.info("Project created successfully with ID: {}", saved.getId());

                                // ---- Notification Integration (safe) ----
                                if (saved.getMemberIds() != null && !saved.getMemberIds().isEmpty()) {
                                    saved.getMemberIds().forEach(memberId -> {
                                        Map<String, Object> payload = new HashMap<>();
                                        payload.put("recipientUserId", memberId); // ✅ FIXED
                                        payload.put("message", "Project " + saved.getName() + " created");
                                        payload.put("projectId", saved.getId());
                                        payload.put("eventType", "PROJECT_CREATED");

                                        // Fire-and-forget subscribe here is acceptable in doOnSuccess
                                        sendNotificationSafe(payload).subscribe(); 
                                    });
                                }
                            })
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
                .doOnSuccess(p -> {
                    log.info("Project updated successfully: {}", p.getId());

                    // ---- Notification Integration (safe) ----
                    if (p.getMemberIds() != null && !p.getMemberIds().isEmpty()) {
                        p.getMemberIds().forEach(memberId -> {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("recipientUserId", memberId); // ✅ FIXED
                            payload.put("message", "Project " + p.getName() + " updated");
                            payload.put("projectId", p.getId());
                            payload.put("eventType", "PROJECT_UPDATED");

                            sendNotificationSafe(payload).subscribe(); // Still fire-and-forget in doOnSuccess
                        });
                    }
                })
                .doOnError(e -> log.error("Error updating project: {}", e.getMessage()));
    }

    // -------------------- DELETE --------------------
    @Override
    public Mono<Void> deleteProject(String projectId) {
        log.info("Deleting project with ID: {}", projectId);

        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(existing -> {
                    String projectName = existing.getName();
                    // Deserialize the list here to ensure the member list is ready before deletion
                    existing.deserializeLists(); 
                    List<String> membersToNotify = existing.getMemberIdsList() != null ? existing.getMemberIdsList() : List.of();
                    
                    // 1. Prepare Notification Monos for all members
                    List<Mono<Void>> notificationMonos = membersToNotify.stream()
                        .map(memberId -> {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("recipientUserId", memberId);
                            payload.put("message", "Project '" + projectName + "' has been deleted");
                            payload.put("projectId", projectId);
                            payload.put("eventType", "PROJECT_DELETED");
                            payload.put("title", projectName); // Pass name for better message on Notification Server
                            
                            return sendNotificationSafe(payload);
                        })
                        .toList();

                    // 2. Notification Phase: Wait for all notifications to be sent
                    Mono<Void> sendNotifications = Mono.when(notificationMonos)
                        .doOnSuccess(v -> publishEvent("project.deleted: " + projectId)); // Publish event after successful notifications

                    // 3. Deletion Phase: Execute deletion only AFTER notifications are sent
                    Mono<Void> deleteOperation = taskServiceClient.deleteTasksByProjectId(projectId)
                            .then(projectRepository.delete(existing));
                            
                    // 4. Chain Notifications before Deletion
                    return sendNotifications
                            .then(deleteOperation)
                            .doOnSuccess(v -> log.info("Project deleted successfully: {}", projectId))
                            .doOnError(e -> log.error("Error deleting project {}: {}", projectId, e.getMessage()));
                });
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
                                membersDTO.getMemberIds().forEach(id -> {
                                    publishEvent("project.member.added: " + id + " -> " + projectId);

                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("recipientUserId", id); // ✅ FIXED
                                    payload.put("message", "You were added to project " + projectId);
                                    payload.put("projectId", projectId);
                                    payload.put("eventType", "PROJECT_MEMBER_ADDED");

                                    sendNotificationSafe(payload).subscribe();
                                });
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
                                membersDTO.getMemberIds().forEach(id -> {
                                    publishEvent("project.member.removed: " + id + " -> " + projectId);

                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("recipientUserId", id); // ✅ FIXED
                                    payload.put("message", "You were removed from project " + projectId);
                                    payload.put("projectId", projectId);
                                    payload.put("eventType", "PROJECT_MEMBER_REMOVED");

                                    sendNotificationSafe(payload).subscribe();
                                });
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