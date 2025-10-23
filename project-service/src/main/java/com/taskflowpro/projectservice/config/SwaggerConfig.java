package com.taskflowpro.projectservice.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger configuration for Project Service.
 * Provides interactive API documentation via OpenAPI.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI projectServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Project Service API")
                        .description("Reactive Project Management Microservice")
                        .version("v1.0")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                )
                .externalDocs(new ExternalDocumentation()
                        .description("Project Service Documentation")
                        .url("https://github.com/your-org/project-service"));
    }
}
