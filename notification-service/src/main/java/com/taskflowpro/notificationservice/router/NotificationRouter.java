package com.taskflowpro.notificationservice.router;

import com.taskflowpro.notificationservice.handler.NotificationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Functional routes for the Notification service.
 */
@Configuration
public class NotificationRouter {

    @Bean
    public RouterFunction<ServerResponse> notificationRoutes(NotificationHandler handler) {
        return RouterFunctions.route()
                .POST("/notifications", handler::pushNotification)
                .GET("/notifications", handler::getAllNotifications)
                .GET("/notifications/{userId}", handler::getNotificationsForUser)
                .GET("/notifications/stream", handler::streamNotifications)
                .PUT("/notifications/{id}/read", handler::markAsRead)
                .build();
    }
}
