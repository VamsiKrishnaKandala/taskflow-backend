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
        
        // --- FIX: Explicitly nest all routes under the /api/v1 prefix ---
        return RouterFunctions.route()
            .path("/api/v1", builder -> builder
                
                // GET /api/v1/analytics/project/{projectId}/velocity
                .GET("/analytics/project/{projectId}/velocity", handler::getProjectVelocity)

                // GET /api/v1/analytics/user/{userId}/summary
                .GET("/analytics/user/{userId}/summary", handler::getUserSummary)
            )
            .build();
        // --- END FIX ---
    }
}