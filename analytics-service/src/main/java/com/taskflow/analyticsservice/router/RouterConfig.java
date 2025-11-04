package com.taskflow.analyticsservice.router;

import com.taskflow.analyticsservice.handler.AnalyticsHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Defines the functional routes for the Analytics Service.
 */
@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> analyticsRoutes(AnalyticsHandler handler) {

        // ✅ Removed /api/v1 prefix to match new routing convention
        return RouterFunctions.route()
            // GET /analytics/project/{projectId}/velocity
            .GET("/analytics/project/{projectId}/velocity", handler::getProjectVelocity)

            // GET /analytics/user/{userId}/summary
            .GET("/analytics/user/{userId}/summary", handler::getUserSummary)

            .build();
    }
}

