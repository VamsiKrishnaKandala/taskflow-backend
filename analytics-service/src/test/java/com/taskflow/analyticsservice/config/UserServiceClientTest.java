package com.taskflow.analyticsservice.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskflow.analyticsservice.dto.UserDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

class UserServiceClientTest {

    private MockWebServer mockWebServer;
    private UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        userServiceClient = new UserServiceClient(WebClient.builder());
        String baseUrl = mockWebServer.url("/").toString();
        // Note: Your UserServiceClient expects the URL to be http://localhost:8080/users/{userId}
        // but your properties file says http://localhost:8080.
        // I will assume the properties file is right and the client appends /users/
        // My mock server URL will be just the base.
        ReflectionTestUtils.setField(userServiceClient, "userServiceUrl", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getUserById_ShouldReturnUser_When200OK() throws JsonProcessingException {
        // Arrange
        UserDTO mockUser = new UserDTO("u1", "Test User", "ROLE_USER");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(mockUser)));

        // Act
        StepVerifier.create(userServiceClient.getUserById("u1", "token", "id", "role"))
                // Assert
                .expectNextMatches(user -> user.id().equals("u1"))
                .verifyComplete();
    }

    @Test
    void getUserById_ShouldReturnEmpty_WhenHttpError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        
        // Act
        StepVerifier.create(userServiceClient.getUserById("u1", "token", "id", "role"))
                // Assert
                .expectNextCount(0)
                .verifyComplete();
    }
}