//package com.taskflowpro.projectservice.exception;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.web.reactive.function.server.ServerRequest;
//import reactor.test.StepVerifier;
//
//import static org.mockito.Mockito.*;
//
///**
// * Unit tests for GlobalExceptionHandler.
// * Covers all custom exceptions including new ones (UnauthorizedAction, ProjectEventPublishing).
// */
//class GlobalExceptionHandlerTest {
//
//    private GlobalExceptionHandler handler;
//    private ServerRequest request;
//
//    @BeforeEach
//    void setup() {
//        handler = new GlobalExceptionHandler();
//        request = mock(ServerRequest.class);
//        when(request.path()).thenReturn("/projects/PF-001");
//    }
//
//    // -------------------- PROJECT NOT FOUND --------------------
//    @Test
//    void testHandleProjectNotFound() {
//        StepVerifier.create(handler.handleProjectNotFound(request, new ProjectNotFoundException("PF-001")))
//                .expectNextMatches(response -> response != null) // basic check for non-null response
//                .verifyComplete();
//    }
//
//    // -------------------- INVALID PROJECT DATA --------------------
//    @Test
//    void testHandleInvalidProjectData() {
//        StepVerifier.create(handler.handleInvalidProjectData(request, new InvalidProjectDataException("Invalid data")))
//                .expectNextMatches(response -> response != null)
//                .verifyComplete();
//    }
//
//    // -------------------- VALIDATION ERROR --------------------
//    @Test
//    void testHandleValidationError() {
//        StepVerifier.create(handler.handleValidationError(request, new IllegalArgumentException("Validation failed")))
//                .expectNextMatches(response -> response != null)
//                .verifyComplete();
//    }
//
//    // -------------------- GENERIC ERROR --------------------
//    @Test
//    void testHandleGenericError() {
//        StepVerifier.create(handler.handleGenericError(request, new RuntimeException("Generic exception")))
//                .expectNextMatches(response -> response != null)
//                .verifyComplete();
//    }
//
//    // -------------------- UNAUTHORIZED ACTION --------------------
//    @Test
//    void testHandleUnauthorizedAction() {
//        StepVerifier.create(handler.handleUnauthorizedAction(request, new UnauthorizedActionException("Forbidden action")))
//                .expectNextMatches(response -> response != null)
//                .verifyComplete();
//    }
//
//    // -------------------- PROJECT EVENT PUBLISHING --------------------
//    @Test
//    void testHandleProjectEventPublishing() {
//        StepVerifier.create(handler.handleProjectEventPublishing(request, new ProjectEventPublishingException("Event failed")))
//                .expectNextMatches(response -> response != null)
//                .verifyComplete();
//    }
//}