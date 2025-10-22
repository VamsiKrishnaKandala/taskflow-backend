package com.taskflowpro.projectservice.repository;

import com.taskflowpro.projectservice.model.Project;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for Project entity.
 */
@Repository
public interface ProjectRepository extends ReactiveCrudRepository<Project, String> {
}