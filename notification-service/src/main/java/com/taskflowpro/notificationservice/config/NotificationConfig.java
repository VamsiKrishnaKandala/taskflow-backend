package com.taskflowpro.notificationservice.config;

import com.taskflowpro.notificationservice.model.Notification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Sinks;

@Configuration
public class NotificationConfig {

    /**
     * Sinks.Many with replay to allow late subscribers to receive past events.
     * - replay().all() retains all events (dangerous memory-wise).
     * - replay().limit(n) keeps only last n (good).
     * - replay().latest() keeps latest only.
     *
     * We'll use replay().limit(100) to keep memory bounded while enabling reasonable replay.
     */
    @Bean
    public Sinks.Many<Notification> notificationSink() {
        return Sinks.many().replay().limit(100);
    }
}
