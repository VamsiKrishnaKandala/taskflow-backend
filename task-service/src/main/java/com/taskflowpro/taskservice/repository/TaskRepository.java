package com.taskflowpro.taskservice.repository;

import com.taskflowpro.taskservice.model.Task;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Reactive repository for Task entity.
 */
@Repository
public interface TaskRepository extends ReactiveCrudRepository<Task, String> {
    /**
     * Find tasks by project id.
     */
    Flux<Task> findByProjectId(String projectId);
}