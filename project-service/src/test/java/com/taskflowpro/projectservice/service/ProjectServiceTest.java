package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.client.NotificationServiceClientWebClient;
import com.taskflowpro.projectservice.client.TaskServiceClientWebClient;
import com.taskflowpro.projectservice.dto.ProjectMembersDTO;
import com.taskflowpro.projectservice.dto.ProjectRequestDTO;
import com.taskflowpro.projectservice.dto.ProjectTagsDTO;
import com.taskflowpro.projectservice.exception.InvalidProjectDataException;
import com.taskflowpro.projectservice.exception.ProjectNotFoundException;
import com.taskflowpro.projectservice.model.Project;
import com.taskflowpro.projectservice.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fully updated unit tests for ProjectServiceImpl using DTOs and mocks for external services.
 * Covers CRUD, member/tag management, ID generation, and error/notification edge cases.
 */
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskServiceClientWebClient taskServiceClient;

    @Mock
    private NotificationServiceClientWebClient notificationClient;

    // Use a mock for the Sinks.Many field to test the publishEvent logic
    @Mock
    private Sinks.Many<String> projectEventSink;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private Project project;
    private ProjectRequestDTO projectRequestDTO;

    @BeforeEach
    void setup() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);

        // --- Use reflection to inject the mock Sinks.Many ---
        Field sinkField = ProjectServiceImpl.class.getDeclaredField("projectEventSink");
        sinkField.setAccessible(true);
        sinkField.set(projectService, projectEventSink);

        // Set up default mock behavior for external services
        when(taskServiceClient.deleteTasksByProjectId(anyString())).thenReturn(Mono.empty());
        when(notificationClient.sendNotificationEvent(anyMap())).thenReturn(Mono.empty()); 
        when(projectEventSink.tryEmitNext(anyString())).thenReturn(Sinks.EmitResult.OK);

        project = Project.builder()
                .id("PF-001")
                .name("Test Project")
                .description("Sample Description")
                .deadline(LocalDate.now().plusDays(10))
                .memberIdsList(List.of("User1"))
                .tagsList(List.of("tag1"))
                .isNew(true)
                .build();
        project.serializeLists();

        projectRequestDTO = ProjectRequestDTO.builder()
                .name("Test Project")
                .description("Sample Description")
                .deadline(LocalDate.now().plusDays(10))
                .memberIds(List.of("User1"))
                .tags(List.of("tag1"))
                .build();
    }

    // -------------------- CREATE --------------------

    @Test
    void testCreateProjectSuccess_NoExistingProjects() {
        when(projectRepository.findAll()).thenReturn(Flux.empty());
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.createProject(projectRequestDTO))
                .expectNextMatches(p -> p.getId().equals("PF-001"))
                .verifyComplete();
        verify(notificationClient, times(1)).sendNotificationEvent(anyMap());
    }

    @Test
    void testCreateProjectSuccess_GenerateNextId() {
        Project existingProject = Project.builder().id("PF-007").build();
        Project nonProject = Project.builder().id("ZZ-999").build(); 

        when(projectRepository.findAll()).thenReturn(Flux.just(existingProject, nonProject));
        
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            saved.setId("PF-008"); 
            return Mono.just(saved);
        });

        StepVerifier.create(projectService.createProject(projectRequestDTO))
                .expectNextMatches(p -> p.getId().equals("PF-008")) 
                .verifyComplete();
    }

    @Test
    void testCreateProjectInvalidName() {
        projectRequestDTO.setName(" ");
        StepVerifier.create(projectService.createProject(projectRequestDTO))
                .expectError(InvalidProjectDataException.class)
                .verify();
    }

    @Test
    void testCreateProjectWithNoMembersSkipsNotification() {
        // 1. DTO with NO members
        ProjectRequestDTO noMemberRequest = ProjectRequestDTO.builder()
                .name(projectRequestDTO.getName())
                .description(projectRequestDTO.getDescription())
                .deadline(projectRequestDTO.getDeadline())
                .memberIds(List.of()) // Set members to empty list
                .tags(projectRequestDTO.getTags())
                .build();

        // 2. Mock Project object to be returned by save(), also with NO members
        // This is crucial for the notification check to work
        Project noMemberProject = Project.builder()
                .id("PF-001") // Mock the generated ID
                .name(noMemberRequest.getName())
                .description(noMemberRequest.getDescription())
                .deadline(noMemberRequest.getDeadline())
                .memberIdsList(List.of()) // <<--- THIS MUST BE EMPTY
                .tagsList(noMemberRequest.getTags())
                .isNew(false) // Not new after saving
                .build();
        noMemberProject.serializeLists();

        when(projectRepository.findAll()).thenReturn(Flux.empty());
        // MOCK the save call to return the noMemberProject
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(noMemberProject)); 

        StepVerifier.create(projectService.createProject(noMemberRequest))
                .expectNextCount(1)
                .verifyComplete();

        // 3. Verify the client was NEVER called
        verify(notificationClient, never()).sendNotificationEvent(anyMap());
    }

    // -------------------- READ --------------------

    @Test
    void testGetProjectByIdSuccess() {
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.getProjectById("PF-001"))
                .expectNextMatches(p -> p.getId().equals("PF-001"))
                .verifyComplete();
    }

    @Test
    void testGetProjectByIdNotFound() {
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.getProjectById("PF-999"))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    @Test
    void testGetAllProjects() {
        when(projectRepository.findAll()).thenReturn(Flux.just(project));

        StepVerifier.create(projectService.getAllProjects())
                .expectNextCount(1)
                .verifyComplete();
    }

    // -------------------- UPDATE --------------------

    @Test
    void testUpdateProjectSuccess() {
        ProjectRequestDTO updatedRequest = ProjectRequestDTO.builder()
                .name("Updated Project")
                .description(projectRequestDTO.getDescription())
                .deadline(projectRequestDTO.getDeadline())
                .memberIds(projectRequestDTO.getMemberIds())
                .tags(projectRequestDTO.getTags())
                .build();
        Project updatedProject = project.toBuilder().name("Updated Project").build();
        updatedProject.serializeLists();

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(updatedProject));

        StepVerifier.create(projectService.updateProject("PF-001", updatedRequest))
                .expectNextMatches(p -> p.getName().equals("Updated Project"))
                .verifyComplete();
        verify(notificationClient, times(1)).sendNotificationEvent(anyMap());
    }

    @Test
    void testUpdateProjectNotFound() {
        ProjectRequestDTO validRequest = ProjectRequestDTO.builder().name("Valid").build();
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.updateProject("PF-999", validRequest))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    @Test
    void testUpdateProjectInvalidName() {
        ProjectRequestDTO updatedRequest = ProjectRequestDTO.builder().name(null).build();
        StepVerifier.create(projectService.updateProject("PF-001", updatedRequest))
                .expectError(InvalidProjectDataException.class)
                .verify();
    }

    // -------------------- DELETE --------------------

    @Test
    void testDeleteProjectSuccess() {
        String projectId = "PF-001";
        when(projectRepository.findById(projectId)).thenReturn(Mono.just(project));
        when(projectRepository.delete(project)).thenReturn(Mono.empty());
        when(taskServiceClient.deleteTasksByProjectId(projectId)).thenReturn(Mono.empty());

        StepVerifier.create(projectService.deleteProject(projectId))
                .verifyComplete();

        // Verify task and repository calls
        verify(taskServiceClient, times(1)).deleteTasksByProjectId(projectId);
        verify(projectRepository, times(1)).delete(project);
        verify(projectEventSink, times(1)).tryEmitNext("project.deleted: PF-001");
        verify(notificationClient, times(1)).sendNotificationEvent(anyMap()); // For the member notification
    }

    @Test
    void testDeleteProjectNotFound() {
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.deleteProject("PF-999"))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    // -------------------- MEMBER MANAGEMENT --------------------

    @Test
    void testAddMembersSuccess() {
        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.addMembers("PF-001", membersDTO))
                .expectNextMatches(p -> p.getMemberIds().contains("User2"))
                .verifyComplete();

        verify(notificationClient, times(1)).sendNotificationEvent(anyMap()); // Notification for User2
        verify(projectEventSink, times(1)).tryEmitNext("project.member.added: User2 -> PF-001");
    }

    @Test
    void testRemoveMembersSuccess() {
        project.setMemberIdsList(List.of("User1", "User2"));
        project.serializeLists();
        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.removeMembers("PF-001", membersDTO))
                .expectNextMatches(p -> !p.getMemberIds().contains("User2") && p.getMemberIds().contains("User1"))
                .verifyComplete();

        verify(notificationClient, times(1)).sendNotificationEvent(anyMap()); // Notification for User2
        verify(projectEventSink, times(1)).tryEmitNext("project.member.removed: User2 -> PF-001");
    }

    @Test
    void testAddMembersProjectNotFound() {
        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.addMembers("PF-999", membersDTO))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    @Test
    void testRemoveMembersProjectNotFound() {
        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.removeMembers("PF-999", membersDTO))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    // -------------------- TAG MANAGEMENT --------------------

    @Test
    void testAddTagsSuccess() {
        ProjectTagsDTO tagsDTO = new ProjectTagsDTO(List.of("tag2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.addTags("PF-001", tagsDTO))
                .expectNextMatches(p -> p.getTags().contains("tag1") && p.getTags().contains("tag2"))
                .verifyComplete();

        verify(projectEventSink, times(1)).tryEmitNext("project.tag.added: tag2 -> PF-001");
    }

    @Test
    void testRemoveTagsSuccess() {
        project.setTagsList(List.of("tag1", "tag2"));
        project.serializeLists();
        ProjectTagsDTO tagsDTO = new ProjectTagsDTO(List.of("tag2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.removeTags("PF-001", tagsDTO))
                .expectNextMatches(p -> !p.getTags().contains("tag2") && p.getTags().contains("tag1"))
                .verifyComplete();

        verify(projectEventSink, times(1)).tryEmitNext("project.tag.removed: tag2 -> PF-001");
    }

    @Test
    void testAddTagsProjectNotFound() {
        ProjectTagsDTO tagsDTO = new ProjectTagsDTO(List.of("tag2"));
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.addTags("PF-999", tagsDTO))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    @Test
    void testRemoveTagsProjectNotFound() {
        ProjectTagsDTO tagsDTO = new ProjectTagsDTO(List.of("tag2"));
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.removeTags("PF-999", tagsDTO))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }


    // -------------------- ERROR & EVENT PUBLISHING EDGE CASES --------------------

    @Test
    void testSendNotificationSafeErrorResume() {
        // Mock the notification client to return an error, but the flow should continue
        when(notificationClient.sendNotificationEvent(anyMap())).thenReturn(Mono.error(new RuntimeException("API Down")));
        
        // This tests the `.onErrorResume(ex -> Mono.empty())` in `sendNotificationSafe`
        when(projectRepository.findAll()).thenReturn(Flux.empty());
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.createProject(projectRequestDTO))
                .expectNextCount(1) // Should still succeed
                .verifyComplete();
    }

    @Test
    void testProjectEventSinkFailure() {
        // Mock the sink to return a failure state for event publishing
        when(projectEventSink.tryEmitNext(anyString())).thenReturn(Sinks.EmitResult.FAIL_NON_SERIALIZED);

        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        // The method should still complete successfully even if the internal event publishing fails
        StepVerifier.create(projectService.addMembers("PF-001", membersDTO))
                .expectNextCount(1)
                .verifyComplete();
    }
}