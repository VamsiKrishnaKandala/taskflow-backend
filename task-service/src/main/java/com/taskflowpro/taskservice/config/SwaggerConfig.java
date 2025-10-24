package com.taskflowpro.taskservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration for Task Service.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI taskServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskFlow Pro - Task Service API")
                        .version("1.0.0")
                        .description("Reactive Task Service API documentation with Project and User validation"));
    }
}