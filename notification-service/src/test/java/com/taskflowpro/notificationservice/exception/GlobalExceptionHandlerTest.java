//package com.taskflowpro.notificationservice.exception;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.MockitoAnnotations;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.mock.web.reactive.function.server.MockServerRequest;
//import org.springframework.web.reactive.function.server.ServerRequest;
//import org.springframework.web.reactive.function.server.ServerResponse;
//import reactor.core.publisher.Mono;
//import reactor.test.StepVerifier;
//
//import java.net.URI;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class GlobalExceptionHandlerTest {
//
//    @InjectMocks
//    private GlobalExceptionHandler exceptionHandler;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//    }
//
//    @Test
//    void handleGenericError_ShouldReturn500AndJsonContentType() {
//        // Arrange
//        String testPath = "/api/test";
//        Throwable testException = new RuntimeException("Simulated failure");
//        ServerRequest mockRequest = MockServerRequest.builder().uri(URI.create(testPath)).build();
//
//        // Act
//        Mono<ServerResponse> responseMono = exceptionHandler.handleGenericError(mockRequest, testException);
//
//        // Assert
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
//                    assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    void badRequest_ShouldReturn400AndJsonContentType() {
//        // Arrange
//        String testPath = "/api/bad-data";
//        String customMessage = "Missing userId";
//        ServerRequest mockRequest = MockServerRequest.builder().uri(URI.create(testPath)).build();
//
//        // Act
//        Mono<ServerResponse> responseMono = exceptionHandler.badRequest(mockRequest, customMessage);
//
//        // Assert
//        StepVerifier.create(responseMono)
//                .assertNext(response -> {
//                    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
//                    assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
//                })
//                .verifyComplete();
//    }
//}