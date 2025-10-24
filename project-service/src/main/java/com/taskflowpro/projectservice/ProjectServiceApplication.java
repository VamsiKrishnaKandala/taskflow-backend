package com.taskflowpro.projectservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Entry point for Project Service application.
 * 
 * Provides WebClient beans for reactive calls to Task Service.
 */
@SpringBootApplication
public class ProjectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }

    /**
     * WebClient for calling Task Service.
     * URL is fetched from application.properties via @Value.
     */
    @Bean
    public WebClient taskServiceWebClient(@Value("${task.service.url}") String taskServiceUrl) {
        return WebClient.builder()
                .baseUrl(taskServiceUrl)
                .build();
    }
}