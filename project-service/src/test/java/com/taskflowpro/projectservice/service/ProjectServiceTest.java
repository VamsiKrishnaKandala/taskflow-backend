package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.dto.ProjectRequestDTO;
import com.taskflowpro.projectservice.dto.ProjectResponseDTO;
import com.taskflowpro.projectservice.dto.ProjectMembersDTO;
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
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Fully updated unit tests for ProjectServiceImpl using DTOs.
 * Covers CRUD, member/tag management, and edge cases.
 */
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private Project project;
    private ProjectRequestDTO projectRequestDTO;

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
    void testCreateProjectSuccess() {
        when(projectRepository.findAll()).thenReturn(Flux.empty());
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.createProject(projectRequestDTO))
                .expectNextMatches(p -> p.getId().equals("PF-001"))
                .verifyComplete();
    }

    @Test
    void testCreateProjectInvalidName() {
        projectRequestDTO.setName(" "); // Invalid name triggers validation
        StepVerifier.create(projectService.createProject(projectRequestDTO))
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
        ProjectRequestDTO updatedRequest = ProjectRequestDTO.builder()
                .name("Updated Project")
                .description("Updated Desc")
                .deadline(LocalDate.now().plusDays(20))
                .memberIds(List.of("User1", "User2"))
                .tags(List.of("tag2"))
                .build();

        Project updatedProject = Project.builder()
                .id("PF-001")
                .name("Updated Project")
                .description("Updated Desc")
                .deadline(LocalDate.now().plusDays(20))
                .memberIdsList(List.of("User1", "User2"))
                .tagsList(List.of("tag2"))
                .build();
        updatedProject.serializeLists();

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(updatedProject));

        StepVerifier.create(projectService.updateProject("PF-001", updatedRequest))
                .expectNextMatches(p -> p.getName().equals("Updated Project"))
                .verifyComplete();
    }

    @Test
    void testUpdateProjectNotFound() {
        ProjectRequestDTO updatedRequest = ProjectRequestDTO.builder()
                .name("Valid Project")
                .build();

        when(projectRepository.findById("PF-999")).thenReturn(Mono.empty());

        StepVerifier.create(projectService.updateProject("PF-999", updatedRequest))
                .expectError(ProjectNotFoundException.class)
                .verify();
    }

    @Test
    void testUpdateProjectInvalidName() {
        ProjectRequestDTO updatedRequest = ProjectRequestDTO.builder()
                .name(" ") // invalid name
                .build();

        StepVerifier.create(projectService.updateProject("PF-001", updatedRequest))
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
        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.addMembers("PF-001", membersDTO))
                .expectNextMatches(p -> p.getMemberIds().contains("User2"))
                .verifyComplete();
    }

    @Test
    void testRemoveMembers() {
        project.setMemberIdsList(List.of("User1", "User2"));
        ProjectMembersDTO membersDTO = new ProjectMembersDTO(List.of("User2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.removeMembers("PF-001", membersDTO))
                .expectNextMatches(p -> !p.getMemberIds().contains("User2"))
                .verifyComplete();
    }

    // -------------------- TAG MANAGEMENT --------------------
    @Test
    void testAddTags() {
        ProjectTagsDTO tagsDTO = new ProjectTagsDTO(List.of("tag2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.addTags("PF-001", tagsDTO))
                .expectNextMatches(p -> p.getTags().contains("tag2"))
                .verifyComplete();
    }

    @Test
    void testRemoveTags() {
        project.setTagsList(List.of("tag1", "tag2"));
        ProjectTagsDTO tagsDTO = new ProjectTagsDTO(List.of("tag2"));

        when(projectRepository.findById("PF-001")).thenReturn(Mono.just(project));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(project));

        StepVerifier.create(projectService.removeTags("PF-001", tagsDTO))
                .expectNextMatches(p -> !p.getTags().contains("tag2"))
                .verifyComplete();
    }
}
