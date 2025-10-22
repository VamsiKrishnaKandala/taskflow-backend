package com.taskflowpro.projectservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler.
 * Covers all custom exceptions.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private ServerRequest request;

    @BeforeEach
    void setup() {
        handler = new GlobalExceptionHandler();
        request = mock(ServerRequest.class);
        when(request.path()).thenReturn("/projects/PF-001");
    }

    @Test
    void testHandleProjectNotFound() {
        StepVerifier.create(handler.handleProjectNotFound(request, new ProjectNotFoundException("PF-001")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void testHandleInvalidProjectData() {
        StepVerifier.create(handler.handleInvalidProjectData(request, new InvalidProjectDataException("Invalid")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void testHandleValidationError() {
        StepVerifier.create(handler.handleValidationError(request, new IllegalArgumentException("Error")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void testHandleGenericError() {
        StepVerifier.create(handler.handleGenericError(request, new RuntimeException("Generic")))
                .expectNextCount(1)
                .verifyComplete();
    }
}
