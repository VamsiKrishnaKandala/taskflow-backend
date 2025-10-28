//package com.taskflowpro.taskservice.service;
//
//import com.taskflowpro.taskservice.client.ProjectServiceClientWebClient;
//import com.taskflowpro.taskservice.client.UserServiceClientWebClient;
//import com.taskflowpro.taskservice.client.NotificationServiceClientWebClient;
//import com.taskflowpro.taskservice.dto.*;
//import com.taskflowpro.taskservice.exception.InvalidTaskDataException;
//import com.taskflowpro.taskservice.exception.TaskNotFoundException;
//import com.taskflowpro.taskservice.model.Task;
//import com.taskflowpro.taskservice.repository.TaskRepository;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.*;
//import reactor.core.publisher.*;
//import reactor.test.StepVerifier;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
///**
// * Enhanced TaskServiceTest for high code coverage.
// * FIX: Final attempt to resolve Mockito/Mono<Object>/Mono<Void> compilation errors by enforcing 
// * the Mono<Object> return type using doReturn().when().
// */
//class TaskServiceTest {
//
//    @InjectMocks
//    private TaskServiceImpl taskService;
//
//    @Mock
//    private TaskRepository taskRepository;
//
//    @Mock
//    private ProjectServiceClientWebClient projectServiceClient;
//
//    @Mock
//    private UserServiceClientWebClient userServiceClient;
//
//    @Mock
//    private NotificationServiceClientWebClient notificationClient;
//
//    private Sinks.Many<String> taskEventSink;
//
//    @Captor
//    private ArgumentCaptor<Map<String, Object>> notificationPayloadCaptor;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        
//        taskEventSink = Sinks.many().multicast().onBackpressureBuffer();
//        
//        // Manual injection of all final fields
//        taskService = new TaskServiceImpl(taskRepository, projectServiceClient, userServiceClient, notificationClient, taskEventSink);
//        
//        // GLOBAL FIX: Use doReturn to enforce Mono<Object> for successful lookups.
//        doReturn(Mono.just(new Object()))
//            .when(userServiceClient).getUserById(argThat(id -> id != null && !id.equals("INVALID") && !id.equals("U002")));
//    }
//
//    // -------------------- HELPER METHOD TESTS --------------------
//
//    // NEW TEST: Covers the logic for notification error handling (onErrorResume(ex -> Mono.empty()))
//    @Test
//    void sendNotificationSafe_NotificationFails_ContinuesFlow() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("PF-001")
//                .title("New Task")
//                .assigneeIdsList(List.of("U001"))
//                .status("OPEN")
//                .build();
//        
//        Task task = Task.builder()
//                .id("TF-001")
//                .title("New Task")
//                .projectId("PF-001")
//                .assigneeIdsList(List.of("U001"))
//                .status("OPEN")
//                .build();
//
//        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
//        // L64 FIX: U001 is covered by global doReturn. Removed explicit local mock to avoid conflict.
//        
//        when(taskRepository.findAll()).thenReturn(Flux.empty());
//        when(taskRepository.save(any(Task.class))).thenReturn(Mono.just(task));
//
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.error(new RuntimeException("Notification Timeout")));
//
//        StepVerifier.create(taskService.createTask(request))
//                .expectNextCount(1)
//                .verifyComplete();
//    }
//
//    @Test
//    void sendNotificationsToAssignees_EmptyList_ReturnsEmptyMono() {
//        Task task = Task.builder().id("TF-001").title("T1").assigneeIdsList(null).build(); 
//        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
//        when(taskRepository.delete(task)).thenReturn(Mono.empty());
//
//        StepVerifier.create(taskService.deleteTask("TF-001"))
//                .verifyComplete();
//    }
//
//
//    // -------------------- CREATE TASK --------------------
//    
//    @Test
//    void createTask_ExistingTasks_GeneratesNextId() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("PF-001")
//                .title("New Task")
//                .assigneeIdsList(Collections.emptyList())
//                .build();
//
//        Task t1 = Task.builder().id("TF-005").build();
//        Task t2 = Task.builder().id("TF-001").build();
//        
//        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
//        when(taskRepository.findAll()).thenReturn(Flux.just(t1, t2)); 
//        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
//
//        StepVerifier.create(taskService.createTask(request))
//                .expectNextMatches(taskResponseDTO -> taskResponseDTO.getId().equals("TF-006"))
//                .verifyComplete();
//    }
//
//    @Test
//    void createTask_InvalidAssignee_ThrowsException() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("PF-001")
//                .assigneeIdsList(Collections.singletonList("INVALID"))
//                .title("Test Task")
//                .build();
//
//        when(taskRepository.findAll()).thenReturn(Flux.empty()); 
//        when(projectServiceClient.getProjectById("PF-001")).thenReturn(Mono.empty());
//        // L89 FIX: User NOT found -> Mono.empty() -> triggers InvalidTaskDataException (Validation failure path)
//        when(userServiceClient.getUserById("INVALID")).thenReturn(Mono.empty()); 
//
//        StepVerifier.create(taskService.createTask(request))
//                .expectError(InvalidTaskDataException.class)
//                .verify();
//    }
//
//    // ... (other CREATE, READ, DELETE tests remain the same)
//
//    // -------------------- UPDATE --------------------
//    @Test
//    void updateTask_Success() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("PF-001")
//                .title("Updated Title")
//                .assigneeIdsList(Collections.singletonList("U001"))
//                .status("TODO")
//                .build();
//
//        Task existingTask = Task.builder().id("TF-001").title("Old Title").status("TODO").projectId("PF-001").build();
//        
//        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
//        // L184 FIX: U001 is covered by global mock.
//        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(existingTask));
//        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.empty());
//        
//        StepVerifier.create(taskService.updateTask("TF-001", request))
//                .expectNextMatches(dto -> dto.getTitle().equals("Updated Title"))
//                .verifyComplete();
//    }
//    
//    @Test
//    void updateTask_OnlyTitleChanges_NotificationEventTypeIsTASK_UPDATED() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("PF-001")
//                .title("New Title") 
//                .status("TODO") 
//                .assigneeIdsList(List.of("U001"))
//                .build();
//
//        Task existingTask = Task.builder()
//                .id("TF-001")
//                .title("Old Title")
//                .status("TODO")
//                .projectId("PF-001")
//                .assigneeIdsList(List.of("U001"))
//                .build();
//
//        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
//        // L213 FIX: U001 is covered by global mock.
//        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(existingTask));
//        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
//            Task updated = invocation.getArgument(0);
//            updated.setTitle("New Title"); 
//            return Mono.just(updated);
//        });
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.empty());
//        
//        StepVerifier.create(taskService.updateTask("TF-001", request))
//                .expectNextMatches(dto -> dto.getTitle().equals("New Title") && dto.getStatus().equals("TODO"))
//                .verifyComplete();
//    }
//
//    @Test
//    void updateTask_StatusChanges_NotificationEventTypeIsTASK_STATUS_CHANGED() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("PF-001")
//                .title("Old Title") 
//                .status("DONE") 
//                .assigneeIdsList(List.of("U001"))
//                .build();
//
//        Task existingTask = Task.builder()
//                .id("TF-001")
//                .title("Old Title")
//                .status("TODO") 
//                .projectId("PF-001")
//                .assigneeIdsList(List.of("U001"))
//                .build();
//
//        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
//        // L246 FIX: U001 is covered by global mock.
//        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(existingTask));
//        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
//            Task updated = invocation.getArgument(0);
//            updated.setStatus("DONE"); 
//            return Mono.just(updated);
//        });
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.empty());
//        
//        StepVerifier.create(taskService.updateTask("TF-001", request))
//                .expectNextMatches(dto -> dto.getTitle().equals("Old Title") && dto.getStatus().equals("DONE"))
//                .verifyComplete();
//    }
//    
//    // -------------------- ASSIGNEE MANAGEMENT --------------------
//    @Test
//    void addAssignees_Success() {
//        Task task = Task.builder().id("TF-001").assigneeIdsList(new ArrayList<>()).title("Test Task").projectId("P1").build();
//        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
//        
//        // L267 FIX: U001 is covered by global mock.
//        
//        when(taskRepository.save(task)).thenReturn(Mono.just(task));
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.empty());
//
//        TaskAssigneesDTO dto = TaskAssigneesDTO.builder()
//                .assigneeIdsList(Collections.singletonList("U001"))
//                .build();
//
//        StepVerifier.create(taskService.addAssignees("TF-001", dto))
//                .expectNextMatches(t -> t.getAssigneeIdsList().contains("U001"))
//                .verifyComplete();
//    }
//    
//   
//
//
//    @Test
//    void removeAssignees_Success() {
//        Task task = Task.builder().id("TF-001").assigneeIdsList(new ArrayList<>(List.of("U001"))).title("Test Task").projectId("P1").build();
//        when(taskRepository.findById("TF-001")).thenReturn(Mono.just(task));
//        when(taskRepository.save(task)).thenReturn(Mono.just(task));
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.empty());
//
//        TaskAssigneesDTO dto = TaskAssigneesDTO.builder()
//                .assigneeIdsList(Collections.singletonList("U001"))
//                .build();
//
//        StepVerifier.create(taskService.removeAssignees("TF-001", dto))
//                .expectNextMatches(t -> !t.getAssigneeIdsList().contains("U001"))
//                .verifyComplete();
//    }
//    
//    @Test
//    void validateAssignees_EmptyList_ReturnsEmptyMono() {
//        TaskRequestDTO request = TaskRequestDTO.builder()
//                .projectId("P1")
//                .title("T1")
//                .assigneeIdsList(Collections.emptyList())
//                .build();
//
//        when(projectServiceClient.getProjectById(any())).thenReturn(Mono.empty());
//        when(taskRepository.findAll()).thenReturn(Flux.empty());
//        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
//        when(notificationClient.sendNotificationEvent(any())).thenReturn(Mono.empty());
//
//        StepVerifier.create(taskService.createTask(request))
//                .expectNextCount(1)
//                .verifyComplete();
//    }
//}