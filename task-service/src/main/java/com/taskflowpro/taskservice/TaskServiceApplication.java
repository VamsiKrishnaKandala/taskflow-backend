package com.taskflowpro.taskservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Sinks;

/**
 * Entry point for Task Service application.
 * 
 * - Registers a global reactive sink for Task events.
 * - Provides WebClient beans for reactive calls to Project Service & User Service.
 * - OpenAPI metadata for Swagger documentation.
 */
@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "TaskFlow Pro - Task Service API",
                version = "1.0",
                description = "Reactive microservice for managing project tasks, assignees, and tags."
        )
)
@Slf4j
public class TaskServiceApplication {

    public static void main(String[] args) {
        log.info("🚀 Starting TaskFlow Pro - Task Service...");
        SpringApplication.run(TaskServiceApplication.class, args);
        log.info("✅ Task Service started successfully.");
    }

    /**
     * Reactive sink used to publish and subscribe to task events across the service.
     */
    @Bean
    public Sinks.Many<String> taskEventSink() {
        log.info("Initializing global TaskEventSink...");
        return Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * WebClient for calling Project Service.
     */
    @Bean
    public WebClient projectServiceWebClient(@Value("${project.service.url}") String projectServiceUrl) {
        return WebClient.builder()
                .baseUrl(projectServiceUrl)
                .build();
    }

    /**
     * WebClient for calling User Service.
     */
    @Bean
    public WebClient userServiceWebClient(@Value("${user.service.url}") String userServiceUrl) {
        return WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }
}