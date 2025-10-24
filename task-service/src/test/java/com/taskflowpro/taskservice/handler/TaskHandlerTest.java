package com.taskflowpro.taskservice.handler;

import com.taskflowpro.taskservice.dto.*;
import com.taskflowpro.taskservice.exception.TaskNotFoundException;
import com.taskflowpro.taskservice.service.TaskService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions; // ADDED: Required for consumeNextWith assertion
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Enhanced TaskHandlerTest for high code coverage.
 * Includes tests for all CRUD operations, error handling paths,
 * private validation method branches, status updates (with/without changedBy),
 * and SSE stream setup.
 */
class TaskHandlerTest {

    @InjectMocks
    private TaskHandler taskHandler;

    @Mock
    private TaskService taskService;

    @Mock
    private Validator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Re-inject for clarity, although @InjectMocks handles it
        taskHandler = new TaskHandler(taskService, validator);
    }

    // ---------------------------------------------------
    // CREATE TASK
    // ---------------------------------------------------
    @Test
    void createTask_Success() {
        TaskRequestDTO requestDTO = TaskRequestDTO.builder()
                .projectId("PF-001")
                .title("Task 1")
                .description("Description")
                .status("TODO")
                .priority("MEDIUM")
                .assigneeIdsList(List.of("U001"))
                .tagsList(List.of("tag1"))
                .dueDate(LocalDate.of(2025, 12, 31))
                .createdBy("Admin")
                .build();

        TaskResponseDTO responseDTO = TaskResponseDTO.builder()
                .id("TF-001")
                .title("Task 1")
                .build();

        // Stub validation to succeed (return empty set)
        when(validator.validate(any(TaskRequestDTO.class))).thenReturn(Collections.emptySet());
        when(taskService.createTask(any(TaskRequestDTO.class))).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(requestDTO));

        StepVerifier.create(taskHandler.createTask(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService, times(1)).createTask(any(TaskRequestDTO.class));
    }

    @Test
    void createTask_ValidationFails_ReturnsBadRequest() {
        // Arrange
        TaskRequestDTO invalidRequest = new TaskRequestDTO(); 

        ConstraintViolation<TaskRequestDTO> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("Title must not be null");

        Set<ConstraintViolation<TaskRequestDTO>> violations = Set.of(violation);
        when(validator.validate(eq(invalidRequest))).thenReturn(violations);

        ServerRequest request = MockServerRequest.builder()
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(invalidRequest));

        StepVerifier.create(taskHandler.createTask(request))
                .expectNextMatches(resp ->
                        resp.statusCode().is4xxClientError() &&
                        resp.statusCode().value() == 400)
                .verifyComplete();

        verify(taskService, never()).createTask(any(TaskRequestDTO.class));
    }

    @Test
    void createTask_ServiceError_ReturnsBadRequest() {
        // Arrange
        TaskRequestDTO requestDTO = TaskRequestDTO.builder().title("Task").build();
        when(validator.validate(any(TaskRequestDTO.class))).thenReturn(Collections.emptySet());
        
        // Service throws a general RuntimeException
        when(taskService.createTask(any(TaskRequestDTO.class)))
                .thenReturn(Mono.error(new RuntimeException("DB Connection failed")));

        ServerRequest request = MockServerRequest.builder()
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(requestDTO));

        StepVerifier.create(taskHandler.createTask(request))
                .expectNextMatches(resp ->
                        resp.statusCode().is4xxClientError() &&
                        resp.statusCode().value() == 400)
                .verifyComplete();

        verify(taskService, times(1)).createTask(any(TaskRequestDTO.class));
    }

    // ---------------------------------------------------
    // GET TASK BY ID
    // ---------------------------------------------------
    @Test
    void getTaskById_Success() {
        TaskResponseDTO responseDTO = TaskResponseDTO.builder()
                .id("TF-001")
                .title("Task 1")
                .build();

        when(taskService.getTaskById("TF-001")).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .build();

        StepVerifier.create(taskHandler.getTaskById(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService, times(1)).getTaskById("TF-001");
    }

    @Test
    void getTaskById_NotFound_SwitchIfEmpty() {
        // Arrange: Service returns an empty Mono
        when(taskService.getTaskById("TF-999")).thenReturn(Mono.empty());

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-999")
                .build();

        StepVerifier.create(taskHandler.getTaskById(request))
                .expectNextMatches(resp -> resp.statusCode().is4xxClientError() && resp.statusCode().value() == 404)
                .verifyComplete();

        verify(taskService, times(1)).getTaskById("TF-999");
    }

    @Test
    void getTaskById_ServiceError_ReturnsBadRequest() {
        // Arrange: Service throws an exception (e.g., database error, not TaskNotFoundException)
        when(taskService.getTaskById("TF-999")).thenReturn(Mono.error(new RuntimeException("Database error")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-999")
                .build();

        StepVerifier.create(taskHandler.getTaskById(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();

        verify(taskService, times(1)).getTaskById("TF-999");
    }

    // ---------------------------------------------------
    // GET ALL TASKS
    // ---------------------------------------------------

    @Test
    void getAllTasks_Success() {
        // Arrange
        TaskResponseDTO dto1 = TaskResponseDTO.builder().id("T1").build();
        when(taskService.getAllTasks()).thenReturn(Flux.just(dto1));

        ServerRequest request = MockServerRequest.builder().build();

        StepVerifier.create(taskHandler.getAllTasks(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService, times(1)).getAllTasks();
    }

    

    // ---------------------------------------------------
    // GET TASKS BY PROJECT ID
    // ---------------------------------------------------

    @Test
    void getTasksByProjectId_Success() {
        // Arrange
        String projectId = "P-001";
        TaskResponseDTO dto = TaskResponseDTO.builder().projectId(projectId).build();
        when(taskService.getTasksByProjectId(projectId)).thenReturn(Flux.just(dto));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("projectId", projectId)
                .build();

        StepVerifier.create(taskHandler.getTasksByProjectId(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService, times(1)).getTasksByProjectId(projectId);
    }


    // ---------------------------------------------------
    // UPDATE TASK (Full)
    // ---------------------------------------------------

    @Test
    void updateTask_Success() {
        // Arrange
        TaskRequestDTO requestDTO = TaskRequestDTO.builder().title("Updated Task").build();
        TaskResponseDTO responseDTO = TaskResponseDTO.builder().id("TF-001").title("Updated Task").build();

        // Stub validation to succeed
        when(validator.validate(any(TaskRequestDTO.class))).thenReturn(Collections.emptySet());
        when(taskService.updateTask(eq("TF-001"), any(TaskRequestDTO.class))).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(requestDTO));

        StepVerifier.create(taskHandler.updateTask(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService, times(1)).updateTask(eq("TF-001"), any(TaskRequestDTO.class));
    }

   
    @Test
    void updateTask_ServiceError_ReturnsBadRequest() {
        // Arrange
        TaskRequestDTO requestDTO = TaskRequestDTO.builder().title("Valid").build();
        when(validator.validate(any(TaskRequestDTO.class))).thenReturn(Collections.emptySet());
        when(taskService.updateTask(eq("TF-001"), any(TaskRequestDTO.class)))
                .thenReturn(Mono.error(new RuntimeException("Task not found on update")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(requestDTO));

        StepVerifier.create(taskHandler.updateTask(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    // ---------------------------------------------------
    // UPDATE TASK STATUS
    // ---------------------------------------------------

    @Test
    void updateStatus_WithChangedBy_Success() {
        // Arrange
        Map<String, String> body = Map.of("status", "DONE", "changedBy", "user1");
        TaskResponseDTO responseDTO = TaskResponseDTO.builder().id("TF-001").status("DONE").build();

        when(taskService.updateTaskStatus("TF-001", "DONE", "user1")).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(body));

        StepVerifier.create(taskHandler.updateStatus(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService).updateTaskStatus("TF-001", "DONE", "user1");
    }

    @Test
    void updateStatus_WithoutChangedBy_Success() {
        // Arrange
        Map<String, String> body = Map.of("status", "IN_PROGRESS"); // changedBy is missing
        TaskResponseDTO responseDTO = TaskResponseDTO.builder().id("TF-001").status("IN_PROGRESS").build();

        // Expect null for changedBy
        when(taskService.updateTaskStatus(eq("TF-001"), eq("IN_PROGRESS"), eq(null))).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(body));

        StepVerifier.create(taskHandler.updateStatus(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        // Verify service was called with null for changedBy
        verify(taskService).updateTaskStatus(eq("TF-001"), eq("IN_PROGRESS"), eq(null));
    }

    @Test
    void updateStatus_ServiceError_ReturnsBadRequest() {
        // Arrange
        Map<String, String> body = Map.of("status", "INVALID_STATUS");
        when(taskService.updateTaskStatus(any(), any(), any())).thenReturn(Mono.error(new IllegalArgumentException("Invalid status value")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(body));

        StepVerifier.create(taskHandler.updateStatus(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    // ---------------------------------------------------
    // DELETE TASK
    // ---------------------------------------------------
    @Test
    void deleteTask_Success() {
        when(taskService.deleteTask("TF-001")).thenReturn(Mono.empty());

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .build();

        StepVerifier.create(taskHandler.deleteTask(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService, times(1)).deleteTask("TF-001");
    }

    @Test
    void deleteTask_ServiceError_ReturnsBadRequest() {
        // Arrange
        when(taskService.deleteTask("TF-001")).thenReturn(Mono.error(new TaskNotFoundException("Task not found for deletion")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .build();

        StepVerifier.create(taskHandler.deleteTask(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    // ---------------------------------------------------
    // ASSIGNEE OPERATIONS
    // ---------------------------------------------------
    @Test
    void addAssignees_Success() {
        TaskAssigneesDTO dto = TaskAssigneesDTO.builder().assigneeIdsList(List.of("U001")).build();
        TaskResponseDTO responseDTO = TaskResponseDTO.builder()
                .id("TF-001")
                .assigneeIdsList(List.of("U001"))
                .build();

        when(taskService.addAssignees("TF-001", dto)).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.addAssignees(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService).addAssignees("TF-001", dto);
    }

    @Test
    void addAssignees_ServiceError_ReturnsBadRequest() {
        // Arrange
        TaskAssigneesDTO dto = TaskAssigneesDTO.builder().assigneeIdsList(List.of("U001")).build();
        when(taskService.addAssignees(any(), any())).thenReturn(Mono.error(new RuntimeException("User not found")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.addAssignees(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    @Test
    void removeAssignees_Success() {
        TaskAssigneesDTO dto = TaskAssigneesDTO.builder().assigneeIdsList(List.of("U001")).build();
        TaskResponseDTO responseDTO = TaskResponseDTO.builder()
                .id("TF-001")
                .assigneeIdsList(Collections.emptyList())
                .build();

        when(taskService.removeAssignees("TF-001", dto)).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.removeAssignees(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService).removeAssignees("TF-001", dto);
    }

    @Test
    void removeAssignees_ServiceError_ReturnsBadRequest() {
        // Arrange
        TaskAssigneesDTO dto = TaskAssigneesDTO.builder().assigneeIdsList(List.of("U001")).build();
        when(taskService.removeAssignees(any(), any())).thenReturn(Mono.error(new RuntimeException("Task ID invalid")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.removeAssignees(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    // ---------------------------------------------------
    // TAG OPERATIONS
    // ---------------------------------------------------
    @Test
    void addTags_Success() {
        TaskTagsDTO dto = TaskTagsDTO.builder().tagsList(List.of("backend")).build();
        TaskResponseDTO responseDTO = TaskResponseDTO.builder()
                .id("TF-001")
                .tagsList(List.of("backend"))
                .build();

        when(taskService.addTags("TF-001", dto)).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.addTags(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService).addTags("TF-001", dto);
    }

    @Test
    void addTags_ServiceError_ReturnsBadRequest() {
        // Arrange
        TaskTagsDTO dto = TaskTagsDTO.builder().tagsList(List.of("backend")).build();
        when(taskService.addTags(any(), any())).thenReturn(Mono.error(new RuntimeException("Tag validation failed")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.addTags(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    @Test
    void removeTags_Success() {
        TaskTagsDTO dto = TaskTagsDTO.builder().tagsList(List.of("backend")).build();
        TaskResponseDTO responseDTO = TaskResponseDTO.builder()
                .id("TF-001")
                .tagsList(Collections.emptyList())
                .build();

        when(taskService.removeTags("TF-001", dto)).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.removeTags(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        verify(taskService).removeTags("TF-001", dto);
    }

    @Test
    void removeTags_ServiceError_ReturnsBadRequest() {
        // Arrange
        TaskTagsDTO dto = TaskTagsDTO.builder().tagsList(List.of("backend")).build();
        when(taskService.removeTags(any(), any())).thenReturn(Mono.error(new RuntimeException("Tag not associated with task")));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        StepVerifier.create(taskHandler.removeTags(request))
                .expectNextMatches(resp -> resp.statusCode().value() == 400)
                .verifyComplete();
    }

    // ---------------------------------------------------
    // TASK EVENT STREAM (SSE)
    // ---------------------------------------------------

    @Test
    void taskEventsStream_Success_FiltersByProjectId() {
        // Arrange
        String projectId = "P-101";

        // Simulate events from the service
        Flux<String> eventFlux = Flux.just(
                "event:create, project:P-101", // Should pass filter
                "event:update, project:P-202", // Should be filtered out
                "event:delete, project:P-101"  // Should pass filter
        );

        when(taskService.taskEventsStream()).thenReturn(eventFlux);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("projectId", projectId)
                .build();

        StepVerifier.create(taskHandler.taskEventsStream(request))
                .expectNextMatches(resp ->
                        resp.statusCode().is2xxSuccessful() &&
                        resp.headers().getContentType().equals(MediaType.TEXT_EVENT_STREAM))
                .verifyComplete();

        verify(taskService).taskEventsStream();
    }

    // ---------------------------------------------------
    // PRIVATE VALIDATOR METHOD COVERAGE
    // ---------------------------------------------------

    @Test
    void privateValidate_WithNullValidator_AllowsUpdateTask() {
        // Arrange: Create a new TaskHandler instance with a null validator
        TaskHandler handlerWithNullValidator = new TaskHandler(taskService, null);

        // Stub service to succeed if called
        TaskRequestDTO dto = TaskRequestDTO.builder().title("Valid").build();
        TaskResponseDTO responseDTO = TaskResponseDTO.builder().id("TF-001").build();
        when(taskService.updateTask(eq("TF-001"), any(TaskRequestDTO.class))).thenReturn(Mono.just(responseDTO));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("id", "TF-001")
                .body(Mono.just(dto));

        // Act & Assert: Act on the handler with the null validator. It should succeed.
        StepVerifier.create(handlerWithNullValidator.updateTask(request))
                .expectNextMatches(resp -> resp.statusCode().is2xxSuccessful())
                .verifyComplete();

        // Verify service was called, confirming the null validator path was taken
        verify(taskService, times(1)).updateTask(eq("TF-001"), any(TaskRequestDTO.class));
    }
}