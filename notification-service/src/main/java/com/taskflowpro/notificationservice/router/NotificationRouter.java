package com.taskflowpro.notificationservice.router;

import com.taskflowpro.notificationservice.handler.NotificationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Functional routes for the Notification service.
 * Relies on GlobalExceptionHandler for error handling.
 */
@Configuration
public class NotificationRouter {

    @Bean
    public RouterFunction<ServerResponse> notificationRoutes(NotificationHandler handler) {
        return RouterFunctions.route()
                // Internal endpoint for other services to create a notification
                .POST("/notifications", handler::pushNotification)
                
                // Get all notifications (Admin only)
                .GET("/notifications", handler::getAllNotifications)

                // Get all notifications for a specific user (Owner or Admin)
                .GET("/notifications/{userId}", handler::getNotificationsForUser)
                
                // SSE stream for a user (Owner only)
                // We change this path to match the project doc
                .GET("/notifications/stream/{userId}", handler::streamNotifications)
                
                // Mark a notification as read (Owner or Admin)
                .PUT("/notifications/{id}/read", handler::markAsRead)
                
                .build();
                // All .onError() blocks are removed; GlobalExceptionHandler handles them.
    }
}