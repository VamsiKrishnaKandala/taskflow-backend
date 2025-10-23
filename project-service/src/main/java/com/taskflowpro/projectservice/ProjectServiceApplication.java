package com.taskflowpro.projectservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


/**
 * Entry point for Project Service application.
 * 
 * Added:
 * - @EnableFeignClients for inter-service communication (e.g., Task Service).
 * 
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.taskflowpro.projectservice.client")
public class ProjectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}