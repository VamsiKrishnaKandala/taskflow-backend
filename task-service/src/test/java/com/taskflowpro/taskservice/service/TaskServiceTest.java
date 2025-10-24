package com.taskflowpro.taskservice.service;

import com.taskflowpro.taskservice.client.ProjectServiceClientWebClient;
import com.taskflowpro.taskservice.client.UserServiceClientWebClient;
import com.taskflowpro.taskservice.dto.*;
import com.taskflowpro.taskservice.exception.InvalidTaskDataException;
import com.taskflowpro.taskservice.exception.TaskNotFoundException;
import com.taskflowpro.taskservice.model.Task;
import com.taskflowpro.taskservice.repository.TaskRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Enhanced TaskServiceTest for high code coverage.
 * Includes tests for all edge cases, error paths (doOnError), and event publishing.
 */
class TaskServiceTest {

    @InjectMocks
    private TaskServiceImpl taskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectServiceClientWebClient projectServiceClient;

    @Mock
    private UserServiceClientWebClient userServiceClient;

    private Sinks.Many<String> taskEventSink;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Use a real Sinks object for testing event publishing
        taskEventSink = Sinks.many().multicast().onBackpressureBuffer();
        // Manually inject the mock Sinks object
        taskService = new TaskServiceImpl(taskRepository, projectServiceClient, userServiceClient, taskEventSink);
    }

    // -------------------- CREATE TASK (Enhanced Coverage) --------------------
    
    // NEW TEST: Covers ID generation logic when existing tasks are present
    @Test
    void createTask_ExistingTasks_GeneratesNextId() {
        TaskRequestDTO request = TaskRequestDTO.builder()
                .projectId("PF-001")
                .title("New Task")
                .assigneeIdsList(Collections.emptyList())
                .build();

        // Simulate existing tasks, max ID is 005. Should generate TF-006.
        Task t1 = Task.builder().id("TF-005").build();
        Task t2 = Task.builder().id("TF-001").build();
        
        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        when(taskRepository.findAll()).thenReturn(Flux.just(t1, t2)); 
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(taskService.createTask(request))
                .expectNextMatches(taskResponseDTO -> taskResponseDTO.getId().equals("TF-006")) // Expect ID TF-006
                .verifyComplete();

        verify(taskRepository).save(argThat(task -> task.getId().equals("TF-006")));
    }

    // Original Test (updated for clarity)
    @Test
    void createTask_Success_IDGenerationFromEmptyRepository() {
        TaskRequestDTO request = TaskRequestDTO.builder()
                .projectId("PF-001")
                .title("New Task")
                .assigneeIdsList(Collections.emptyList())
                .build();

        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        when(taskRepository.findAll()).thenReturn(Flux.empty()); // No existing tasks
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(taskService.createTask(request))
                .expectNextMatches(taskResponseDTO -> taskResponseDTO.getId().equals("TF-001")) // Expect ID TF-001
                .verifyComplete();

        verify(taskRepository).save(argThat(task -> task.getId().equals("TF-001")));
    }

    @Test
    void createTask_InvalidAssignee_ThrowsException() {
        TaskRequestDTO request = TaskRequestDTO.builder()
                .projectId("PF-001")
                .assigneeIdsList(Collections.singletonList("INVALID"))
                .title("Test Task")
                .build();

        when(taskRepository.findAll()).thenReturn(Flux.empty()); 
        when(projectServiceClient.getProjectById("PF-001")).thenReturn(Mono.empty());
        when(userServiceClient.getUserById("INVALID")).thenReturn(Mono.empty()); // Invalid user returns empty mono

        StepVerifier.create(taskService.createTask(request))
                .expectError(InvalidTaskDataException.class)
                .verify();
        
        verify(taskRepository, never()).save(any(Task.class));
    }
    
    // NEW TEST: Covers validation failure when project is not found
    @Test
    void createTask_ProjectValidationFails_ThrowsException() {
        TaskRequestDTO request = TaskRequestDTO.builder()
                .projectId("PF-999")
                .assigneeIdsList(Collections.emptyList())
                .title("Test Task")
                .build();

        when(taskRepository.findAll()).thenReturn(Flux.empty()); 
        // Project service client returns an error for invalid project ID
        when(projectServiceClient.getProjectById("PF-999")).thenReturn(Mono.error(new InvalidTaskDataException("Project not found")));

        StepVerifier.create(taskService.createTask(request))
                .expectError(InvalidTaskDataException.class)
                .verify();
        
        verify(taskRepository, never()).save(any(Task.class));
    }

    // NEW TEST: Covers doOnError branch when repository save fails
    @Test
    void createTask_RepositorySaveError_ThrowsException() {
        TaskRequestDTO request = TaskRequestDTO.builder().projectId("PF-001").assigneeIdsList(Collections.emptyList()).title("New Task").build();

        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        when(taskRepository.findAll()).thenReturn(Flux.empty());
        when(taskRepository.save(any(Task.class))).thenReturn(Mono.error(new RuntimeException("DB Save Error")));

        StepVerifier.create(taskService.createTask(request))
                .expectErrorSatisfies(throwable -> 
                    Assertions.assertTrue(throwable instanceof RuntimeException && throwable.getMessage().equals("DB Save Error")))
                .verify();
    }


    // -------------------- READ --------------------
    @Test
    void getTaskById_Success() {
        Task task = Task.builder().id("TF-001").title("Task 1").build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));

        StepVerifier.create(taskService.getTaskById("TF-001"))
                .expectNextMatches(dto -> dto.getId().equals("TF-001"))
                .verifyComplete();
    }

    @Test
    void getTaskById_NotFound() {
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.getTaskById("TF-999"))
                .expectError(TaskNotFoundException.class)
                .verify();
    }

    // NEW TEST: Covers doOnError branch when repository find fails
    @Test
    void getTaskById_RepositoryError_ThrowsException() {
        when(taskRepository.findById("TF-999")).thenReturn(Mono.error(new RuntimeException("DB Find Error")));

        StepVerifier.create(taskService.getTaskById("TF-999"))
                .expectErrorSatisfies(throwable -> 
                    Assertions.assertTrue(throwable instanceof RuntimeException && throwable.getMessage().equals("DB Find Error")))
                .verify();
    }

    // NEW TEST: Covers success path for getAllTasks
    @Test
    void getAllTasks_Success() {
        Task task1 = Task.builder().id("T1").title("Task 1").build();
        Task task2 = Task.builder().id("T2").title("Task 2").build();
        when(taskRepository.findAll()).thenReturn(Flux.just(task1, task2));

        StepVerifier.create(taskService.getAllTasks())
                .expectNextCount(2)
                .verifyComplete();
        verify(taskRepository, times(1)).findAll();
    }

    // NEW TEST: Covers doOnError branch for getAllTasks
    @Test
    void getAllTasks_RepositoryError_ThrowsException() {
        when(taskRepository.findAll()).thenReturn(Flux.error(new RuntimeException("DB Timeout")));

        StepVerifier.create(taskService.getAllTasks())
                .expectErrorSatisfies(throwable -> 
                    Assertions.assertTrue(throwable instanceof RuntimeException && throwable.getMessage().equals("DB Timeout")))
                .verify();
    }
    
    // NEW TEST: Covers success path for getTasksByProjectId
    @Test
    void getTasksByProjectId_Success() {
        Task task1 = Task.builder().id("T1").projectId("P1").build();
        when(taskRepository.findByProjectId("P1")).thenReturn(Flux.just(task1));

        StepVerifier.create(taskService.getTasksByProjectId("P1"))
                .expectNextCount(1)
                .verifyComplete();
        verify(taskRepository, times(1)).findByProjectId("P1");
    }

    // NEW TEST: Covers doOnError branch for getTasksByProjectId
    @Test
    void getTasksByProjectId_RepositoryError_ThrowsException() {
        when(taskRepository.findByProjectId(any())).thenReturn(Flux.error(new RuntimeException("DB Timeout")));

        StepVerifier.create(taskService.getTasksByProjectId("P1"))
                .expectErrorSatisfies(throwable -> 
                    Assertions.assertTrue(throwable instanceof RuntimeException && throwable.getMessage().equals("DB Timeout")))
                .verify();
    }
    
    // -------------------- UPDATE --------------------
    // NEW TEST: Covers success path for updateTask
    @Test
    void updateTask_Success() {
        TaskRequestDTO request = TaskRequestDTO.builder()
                .projectId("PF-001")
                .title("Updated Title")
                .assigneeIdsList(Collections.singletonList("U001"))
                .build();

        Task existingTask = Task.builder().id("TF-001").title("Old Title").projectId("PF-001").build();
        
        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        doReturn(Mono.just(new Object())).when(userServiceClient).getUserById("U001");
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(existingTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        
        StepVerifier.create(taskService.updateTask("TF-001", request))
                .expectNextMatches(dto -> dto.getTitle().equals("Updated Title"))
                .verifyComplete();
                
        // Ensure that validation and save were called
        verify(projectServiceClient).getProjectById("PF-001");
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    // NEW TEST: Covers NotFound path for updateTask
    @Test
    void updateTask_NotFound_ThrowsException() {
        TaskRequestDTO request = TaskRequestDTO.builder().projectId("P1").title("Updated").assigneeIdsList(Collections.emptyList()).build();
        
        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.updateTask("TF-999", request))
                .expectError(TaskNotFoundException.class)
                .verify();
    }
    
    // NEW TEST: Covers doOnError branch on save during updateTask
    @Test
    void updateTask_RepositorySaveError_ThrowsException() {
        TaskRequestDTO request = TaskRequestDTO.builder().projectId("P1").title("Updated").assigneeIdsList(Collections.emptyList()).build();
        Task existingTask = Task.builder().id("TF-001").build();
        
        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(existingTask));
        when(taskRepository.save(any(Task.class))).thenReturn(Mono.error(new RuntimeException("Update Save Failed")));
        
        StepVerifier.create(taskService.updateTask("TF-001", request))
                .expectErrorSatisfies(throwable -> 
                    Assertions.assertTrue(throwable instanceof RuntimeException && throwable.getMessage().equals("Update Save Failed")))
                .verify();
    }

    // -------------------- DELETE TASK --------------------
    @Test
    void deleteTask_Success() {
        Task task = Task.builder().id("TF-001").build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        when(taskRepository.delete(task)).thenReturn(Mono.empty()); 

        StepVerifier.create(taskService.deleteTask("TF-001"))
                .verifyComplete();

        verify(taskRepository, times(1)).delete(task);
    }

    @Test
    void deleteTask_NotFound() {
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.deleteTask("TF-999"))
                .expectError(TaskNotFoundException.class)
                .verify();
    }

    // NEW TEST: Covers doOnError branch when repository delete fails
    @Test
    void deleteTask_RepositoryDeleteError_ThrowsException() {
        Task task = Task.builder().id("TF-001").build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        when(taskRepository.delete(task)).thenReturn(Mono.error(new RuntimeException("Delete Failed")));

        StepVerifier.create(taskService.deleteTask("TF-001"))
                .expectErrorSatisfies(throwable -> 
                    Assertions.assertTrue(throwable instanceof RuntimeException && throwable.getMessage().equals("Delete Failed")))
                .verify();
    }

    // -------------------- STATUS UPDATE --------------------
    // NEW TEST: Covers success path for updateTaskStatus
    @Test
    void updateTaskStatus_Success() {
        Task task = Task.builder().id("TF-001").status("TODO").build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        
        StepVerifier.create(taskService.updateTaskStatus("TF-001", "DONE", "user1"))
                .expectNextMatches(dto -> dto.getStatus().equals("DONE"))
                .verifyComplete();
                
        verify(taskRepository).save(argThat(t -> t.getStatus().equals("DONE")));
    }

    // NEW TEST: Covers NotFound path for updateTaskStatus
    @Test
    void updateTaskStatus_NotFound_ThrowsException() {
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.updateTaskStatus("TF-999", "DONE", "user1"))
                .expectError(TaskNotFoundException.class)
                .verify();
    }

    // -------------------- ASSIGNEE MANAGEMENT --------------------
    @Test
    void addAssignees_Success() {
        Task task = Task.builder().id("TF-001").assigneeIdsList(new ArrayList<>()).build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        
        doReturn(Mono.just(new Object())).when(userServiceClient).getUserById("U001");
        
        when(taskRepository.save(task)).thenReturn(Mono.just(task));

        TaskAssigneesDTO dto = TaskAssigneesDTO.builder()
                .assigneeIdsList(Collections.singletonList("U001"))
                .build();

        StepVerifier.create(taskService.addAssignees("TF-001", dto))
                .expectNextMatches(t -> t.getAssigneeIdsList().contains("U001"))
                .verifyComplete();
    }
    
    // NEW TEST: Covers NotFound path for addAssignees
    @Test
    void addAssignees_NotFound_ThrowsException() {
        TaskAssigneesDTO dto = TaskAssigneesDTO.builder().assigneeIdsList(Collections.singletonList("U001")).build();
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.addAssignees("TF-999", dto))
                .expectError(TaskNotFoundException.class)
                .verify();
    }
    
    // NEW TEST: Covers validation success when assignee list is empty (L207)
    @Test
    void validateAssignees_EmptyList_ReturnsEmptyMono() {
        TaskRequestDTO request = TaskRequestDTO.builder()
                .projectId("P1")
                .title("T1")
                .assigneeIdsList(Collections.emptyList()) // This triggers the short-circuit validation path
                .build();

        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
        when(taskRepository.findAll()).thenReturn(Flux.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Expect successful completion, validating that the empty list short-circuited the validation
        StepVerifier.create(taskService.createTask(request))
                .expectNextCount(1)
                .verifyComplete();
    }


    @Test
    void removeAssignees_Success() {
        Task task = Task.builder().id("TF-001").assigneeIdsList(new ArrayList<>(List.of("U001"))).build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        when(taskRepository.save(task)).thenReturn(Mono.just(task));

        TaskAssigneesDTO dto = TaskAssigneesDTO.builder()
                .assigneeIdsList(Collections.singletonList("U001"))
                .build();

        StepVerifier.create(taskService.removeAssignees("TF-001", dto))
                .expectNextMatches(t -> !t.getAssigneeIdsList().contains("U001"))
                .verifyComplete();
    }

    // -------------------- TAG MANAGEMENT --------------------
    // NEW TEST: Covers success path for addTags
    @Test
    void addTags_Success() {
        Task task = Task.builder().id("TF-001").tagsList(new ArrayList<>()).build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        when(taskRepository.save(task)).thenReturn(Mono.just(task));

        TaskTagsDTO dto = TaskTagsDTO.builder()
                .tagsList(Collections.singletonList("design"))
                .build();

        StepVerifier.create(taskService.addTags("TF-001", dto))
                .expectNextMatches(t -> t.getTagsList().contains("design"))
                .verifyComplete();
    }
    
    // NEW TEST: Covers NotFound path for addTags
    @Test
    void addTags_NotFound_ThrowsException() {
        TaskTagsDTO dto = TaskTagsDTO.builder().tagsList(Collections.singletonList("design")).build();
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.addTags("TF-999", dto))
                .expectError(TaskNotFoundException.class)
                .verify();
    }

    // NEW TEST: Covers success path for removeTags
    @Test
    void removeTags_Success() {
        Task task = Task.builder().id("TF-001").tagsList(new ArrayList<>(List.of("design", "bug"))).build();
        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
        when(taskRepository.save(task)).thenReturn(Mono.just(task));

        TaskTagsDTO dto = TaskTagsDTO.builder()
                .tagsList(Collections.singletonList("design"))
                .build();

        StepVerifier.create(taskService.removeTags("TF-001", dto))
                .expectNextMatches(t -> !t.getTagsList().contains("design") && t.getTagsList().contains("bug"))
                .verifyComplete();
    }

    // NEW TEST: Covers NotFound path for removeTags
    @Test
    void removeTags_NotFound_ThrowsException() {
        TaskTagsDTO dto = TaskTagsDTO.builder().tagsList(Collections.singletonList("design")).build();
        when(taskRepository.findById("TF-999")).thenReturn(Mono.empty());

        StepVerifier.create(taskService.removeTags("TF-999", dto))
                .expectError(TaskNotFoundException.class)
                .verify();
    }

    // -------------------- TASK EVENT STREAM --------------------
    // NEW TEST: Covers the taskEventsStream method
    @Test
    void taskEventsStream_ReturnsFlux() {
        StepVerifier.create(taskService.taskEventsStream())
                .expectSubscription()
                .then(() -> taskEventSink.tryEmitNext("EVENT_1"))
                .expectNext("EVENT_1")
                .thenCancel()
                .verify();
    }
}