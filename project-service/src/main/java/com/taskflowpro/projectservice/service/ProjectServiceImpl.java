package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.client.NotificationServiceClientWebClient;
import com.taskflowpro.projectservice.client.TaskServiceClientWebClient;
import com.taskflowpro.projectservice.dto.*;
import com.taskflowpro.projectservice.exception.AccessDeniedException;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    // --- MODIFIED: sendNotificationSafe now requires auth headers ---
    private Mono<Void> sendNotificationSafe(Map<String, Object> payload, String authorizationHeader, String requesterId, String requesterRole) {
        // Pass auth headers to the notification client
        return notificationClient.sendNotificationEvent(payload, authorizationHeader, requesterId, requesterRole)
                .doOnError(ex -> log.error("Notification failed: {}", ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    // --- Helper for Role Checking ---
    private boolean isAdmin(String requesterRole) { return "ROLE_ADMIN".equals(requesterRole); }
    private boolean isManager(String requesterRole) { return "ROLE_MANAGER".equals(requesterRole); }

    // -------------------- CREATE --------------------
    @Override
    public Mono<ProjectResponseDTO> createProject(ProjectRequestDTO projectRequest, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to create new project: {} by User: {}, Role: {}", projectRequest.getName(), requesterId, requesterRole);

        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to create a project.", requesterId, requesterRole);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can create projects."));
        }

        if (projectRequest.getName() == null || projectRequest.getName().isBlank()) {
            return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
        }

        return projectRepository.findAll()
                .map(Project::getId)
                .filter(id -> id != null && id.startsWith("PF-"))
                .map(id -> Integer.parseInt(id.substring(3)))
                .sort()
                .last(0)
                .defaultIfEmpty(0) // Handle case for first project
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

                    project.serializeLists(); // Convert lists to strings
                    log.info("Generated new project ID: {}", nextId);

                    return projectRepository.save(project)
                            .map(saved -> {
                                saved.deserializeLists();
                                return mapToDTO(saved);
                            })
                            .doOnSuccess(saved -> {
                                log.info("Project created successfully with ID: {}", saved.getId());
                                if (saved.getMemberIds() != null && !saved.getMemberIds().isEmpty()) {
                                    saved.getMemberIds().forEach(memberId -> {
                                        Map<String, Object> payload = new HashMap<>();
                                        payload.put("recipientUserId", memberId);
                                        payload.put("message", "Project " + saved.getName() + " created");
                                        payload.put("projectId", saved.getId());
                                        payload.put("eventType", "PROJECT_CREATED");
                                        // --- PASS AUTH HEADERS ---
                                        sendNotificationSafe(payload, authorizationHeader, requesterId, requesterRole).subscribe();
                                    });
                                }
                            })
                            .doOnError(error -> log.error("Error creating project: {}", error.getMessage()));
                });
    }

    // -------------------- READ --------------------
    @Override
    public Mono<ProjectResponseDTO> getProjectById(String projectId, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Fetching project ID: {} by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(project -> {
                    project.deserializeLists(); 
                    boolean isMember = project.getMemberIdsList() != null && project.getMemberIdsList().contains(requesterId);
                    if (!isAdmin(requesterRole) && !isManager(requesterRole) && !isMember) {
                         log.warn("Access Denied: User {} ({}) attempted to access project {}", requesterId, requesterRole, projectId);
                         return Mono.error(new AccessDeniedException("You are not authorized to view this project."));
                    }
                    log.info("Access granted. Project retrieved successfully: {}", project.getName());
                    return Mono.just(mapToDTO(project));
                })
                .doOnError(e -> log.error("Error fetching project {}: {}", projectId, e.getMessage()));
    }

    @Override
    public Flux<ProjectResponseDTO> getAllProjects(String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Fetching all projects for User: {}, Role: {}", requesterId, requesterRole);

        if (isAdmin(requesterRole)) {
            log.info("Admin access: fetching all projects.");
            return projectRepository.findAll()
                    .map(p -> {
                        p.deserializeLists();
                        return mapToDTO(p);
                    });
        } else if (isManager(requesterRole)) {
            log.info("Manager access: fetching projects for member ID: {}", requesterId);
            return projectRepository.findAll()
                    .map(p -> {
                        p.deserializeLists();
                        return p;
                    })
                    .filter(p -> p.getMemberIdsList() != null && p.getMemberIdsList().contains(requesterId))
                    .map(this::mapToDTO);
        } else {
            log.warn("Access Denied: User {} ({}) attempted to list all projects.", requesterId, requesterRole);
            return Flux.error(new AccessDeniedException("Only ADMIN or MANAGER users can list all projects."));
        }
    }

    // -------------------- UPDATE --------------------
    @Override
    public Mono<ProjectResponseDTO> updateProject(String projectId, ProjectRequestDTO projectRequest, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Updating project ID: {} by User: {}, Role: {}", projectId, requesterId, requesterRole);

        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to update project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can update projects."));
        }
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
                    if (p.getMemberIds() != null && !p.getMemberIds().isEmpty()) {
                        p.getMemberIds().forEach(memberId -> {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("recipientUserId", memberId);
                            payload.put("message", "Project " + p.getName() + " updated");
                            payload.put("projectId", p.getId());
                            payload.put("eventType", "PROJECT_UPDATED");
                            // --- PASS AUTH HEADERS ---
                            sendNotificationSafe(payload, authorizationHeader, requesterId, requesterRole).subscribe();
                        });
                    }
                })
                .doOnError(e -> log.error("Error updating project: {}", e.getMessage()));
    }

    // -------------------- DELETE --------------------
    @Override
    public Mono<Void> deleteProject(String projectId, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Attempting to delete project ID: {} by User: {}, Role: {}", projectId, requesterId, requesterRole);

        if (!isAdmin(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to delete project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN users can delete projects."));
        }

        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException(projectId)))
                .flatMap(existing -> {
                    String projectName = existing.getName();
                    existing.deserializeLists();
                    List<String> membersToNotify = existing.getMemberIdsList() != null ? existing.getMemberIdsList() : List.of();

                    List<Mono<Void>> notificationMonos = membersToNotify.stream()
                            .map(memberId -> {
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("recipientUserId", memberId);
                                payload.put("message", "Project '" + projectName + "' has been deleted");
                                payload.put("projectId", projectId);
                                payload.put("eventType", "PROJECT_DELETED");
                                payload.put("title", projectName);
                                // --- PASS AUTH HEADERS ---
                                return sendNotificationSafe(payload, authorizationHeader, requesterId, requesterRole);
                            })
                            .collect(Collectors.toList());

                    Mono<Void> sendNotifications = Mono.when(notificationMonos)
                            .doOnSuccess(v -> publishEvent("project.deleted: " + projectId));

                    // --- PASS AUTH HEADERS ---
                    Mono<Void> deleteOperation = taskServiceClient.deleteTasksByProjectId(projectId, requesterId, requesterRole, authorizationHeader)
                            .then(projectRepository.delete(existing));

                    return sendNotifications
                            .then(deleteOperation)
                            .doOnSuccess(v -> log.info("Project deleted successfully: {}", projectId))
                            .doOnError(e -> log.error("Error deleting project {}: {}", projectId, e.getMessage()));
                });
    }

    // -------------------- MEMBER MANAGEMENT --------------------
    @Override
    public Mono<ProjectResponseDTO> addMembers(String projectId, ProjectMembersDTO membersDTO, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Adding members {} to project {} by User: {}, Role: {}", membersDTO.getMemberIds(), projectId, requesterId, requesterRole);
        
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to add members to project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can add members."));
        }
        
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
                                    payload.put("recipientUserId", id);
                                    payload.put("message", "You were added to project " + projectId);
                                    payload.put("projectId", projectId);
                                    payload.put("eventType", "PROJECT_MEMBER_ADDED");
                                    // --- PASS AUTH HEADERS ---
                                    sendNotificationSafe(payload, authorizationHeader, requesterId, requesterRole).subscribe();
                                });
                                return mapToDTO(saved);
                            });
                });
    }

    @Override
    public Mono<ProjectResponseDTO> removeMembers(String projectId, ProjectMembersDTO membersDTO, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Removing members {} from project {} by User: {}, Role: {}", membersDTO.getMemberIds(), projectId, requesterId, requesterRole);
        
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to remove members from project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can remove members."));
        }

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
                                    payload.put("recipientUserId", id);
                                    payload.put("message", "You were removed from project " + projectId);
                                    payload.put("projectId", projectId);
                                    payload.put("eventType", "PROJECT_MEMBER_REMOVED");
                                    // --- PASS AUTH HEADERS ---
                                    sendNotificationSafe(payload, authorizationHeader, requesterId, requesterRole).subscribe();
                                });
                                return mapToDTO(saved);
                            });
                });
    }

    // -------------------- TAG MANAGEMENT --------------------
    @Override
    public Mono<ProjectResponseDTO> addTags(String projectId, ProjectTagsDTO tagsDTO, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Adding tags {} to project {} by User: {}, Role: {}", tagsDTO.getTags(), projectId, requesterId, requesterRole);
        
        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to add tags to project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can add tags."));
        }

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
    public Mono<ProjectResponseDTO> removeTags(String projectId, ProjectTagsDTO tagsDTO, String requesterId, String requesterRole, String authorizationHeader) {
        log.info("Removing tags {} from project {} by User: {}, Role: {}", tagsDTO.getTags(), projectId, requesterId, requesterRole);

        if (!isAdmin(requesterRole) && !isManager(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to remove tags from project {}", requesterId, requesterRole, projectId);
            return Mono.error(new AccessDeniedException("Only ADMIN or MANAGER users can remove tags."));
        }
        
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
                .memberIds(project.getMemberIdsList()) // Use the deserialized list
                .tags(project.getTagsList()) // Use the deserialized list
                .build();
    }
}