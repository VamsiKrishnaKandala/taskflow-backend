//package com.taskflowpro.taskservice.exception;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.http.HttpStatus;
//import org.springframework.web.reactive.function.server.ServerRequest;
//import org.springframework.web.reactive.function.server.ServerResponse;
//import reactor.core.publisher.Mono;
//import reactor.test.StepVerifier;
//
//import java.net.URI;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//class GlobalExceptionHandlerTest {
//
//    private GlobalExceptionHandler exceptionHandler;
//    private ServerRequest mockRequest;
//
//    @BeforeEach
//    void setUp() {
//        exceptionHandler = new GlobalExceptionHandler();
//        mockRequest = mock(ServerRequest.class);
//        when(mockRequest.path()).thenReturn("/api/tasks/test");
//    }
//
//    @Test
//    void handleTaskNotFound_ShouldReturn404() {
//        TaskNotFoundException ex = new TaskNotFoundException("Task not found");
//
//        Mono<ServerResponse> responseMono = exceptionHandler.handleTaskNotFound(mockRequest, ex);
//
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    void handleInvalidTaskData_ShouldReturn400() {
//        InvalidTaskDataException ex = new InvalidTaskDataException("Invalid data provided");
//
//        Mono<ServerResponse> responseMono = exceptionHandler.handleInvalidTaskData(mockRequest, ex);
//
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    void handleUnauthorized_ShouldReturn403() {
//        UnauthorizedActionException ex = new UnauthorizedActionException("Not allowed");
//
//        Mono<ServerResponse> responseMono = exceptionHandler.handleUnauthorized(mockRequest, ex);
//
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.FORBIDDEN, response.statusCode());
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    void handleTaskEventPublishing_ShouldReturn500() {
//        TaskEventPublishingException ex = new TaskEventPublishingException("Failed to publish");
//
//        Mono<ServerResponse> responseMono = exceptionHandler.handleTaskEventPublishing(mockRequest, ex);
//
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    void handleGenericError_ShouldReturn500() {
//        RuntimeException ex = new RuntimeException("Unexpected error");
//
//        Mono<ServerResponse> responseMono = exceptionHandler.handleGenericError(mockRequest, ex);
//
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
//                })
//                .verifyComplete();
//    }
//}