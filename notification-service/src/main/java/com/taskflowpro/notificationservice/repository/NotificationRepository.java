package com.taskflowpro.notificationservice.repository;

import com.taskflowpro.notificationservice.model.Notification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for Notification persistence.
 */
public interface NotificationRepository extends ReactiveCrudRepository<Notification, String> {

    /**
     * Find notifications for a user, ordered by createdAt descending.
     */
    Flux<Notification> findAllByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * ✅ Fetch the most recently created notification (for ID generation).
     * Used to safely determine the latest NF-XXX sequence number.
     */
    Mono<Notification> findTopByOrderByIdDesc();
}
