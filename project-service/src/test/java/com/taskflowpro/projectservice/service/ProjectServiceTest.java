package com.taskflowpro.projectservice.service;

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
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Fully corrected unit tests for ProjectService.
 * Covers CRUD, member/tag management, and edge cases.
 */
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    private Project project;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        project = Project.builder()
                .id("PF-001")
                .name("Test Project")
                .description("Sample Description")
                .deadline(LocalDate.now().plusDays(10))
                .memberIdsList(List.of("User1"))
                .tagsList(List.of("tag1"))
                .build();
    }

    // -------------------- CREATE --------------------

    @Test
    void testCreateProjectSuccess() {
        when(projectRepository.findAll()).thenReturn(Flux.empty());
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.createProject(project))
                .expectNextMatches(p -> p.getId().equals("PF-001"))
                .verifyComplete();
    }

    @Test
    void testCreateProjectInvalidName() {
        project.setName(" "); // Invalid name triggers validation
        StepVerifier.create(projectService.createProject(project))
                .expectError(InvalidProjectDataException.class)
                .verify();
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
        Project updated = Project.builder()
                .name("Updated Project")
                .description("Updated Desc")
                .deadline(LocalDate.now().plusDays(20))
                .memberIdsList(List.of("User1", "User2"))
                .tagsList(List.of("tag2"))
                .build();

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(updated));

        StepVerifier.create(projectService.updateProject("PF-001", updated))
                .expectNextMatches(p -> p.getName().equals("Updated Project"))
                .verifyComplete();
    }

    @Test
    void testUpdateProjectNotFound() {
        Project updated = new Project();
        updated.setName("Valid Project"); // must be non-blank to pass validation
        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.updateProject("PF-999", updated))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    @Test
    void testUpdateProjectInvalidName() {
        Project updated = new Project();
        updated.setName(" "); // invalid name triggers validation
        StepVerifier.create(projectService.updateProject("PF-001", updated))
                .expectError(InvalidProjectDataException.class)
                .verify();
    }

    // -------------------- DELETE --------------------

    @Test
    void testDeleteProjectSuccess() {
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.delete(project)).thenReturn(Mono.empty());

        StepVerifier.create(projectService.deleteProject("PF-001"))
                .verifyComplete();
        verify(projectRepository, times(1)).delete(project);
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
    void testAddMembers() {
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.addMembers("PF-001", List.of("User2")))
                .expectNextMatches(p -> p.getMemberIdsList().contains("User2"))
                .verifyComplete();
    }

    @Test
    void testRemoveMembers() {
        project.setMemberIdsList(List.of("User1", "User2"));
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.removeMembers("PF-001", List.of("User2")))
                .expectNextMatches(p -> !p.getMemberIdsList().contains("User2"))
                .verifyComplete();
    }

    // -------------------- TAG MANAGEMENT --------------------

    @Test
    void testAddTags() {
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.addTags("PF-001", List.of("tag2")))
                .expectNextMatches(p -> p.getTagsList().contains("tag2"))
                .verifyComplete();
    }

    @Test
    void testRemoveTags() {
        project.setTagsList(List.of("tag1", "tag2"));
        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.removeTags("PF-001", List.of("tag2")))
                .expectNextMatches(p -> !p.getTagsList().contains("tag2"))
                .verifyComplete();
    }
}
