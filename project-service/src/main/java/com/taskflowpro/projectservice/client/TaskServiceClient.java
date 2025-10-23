package com.taskflowpro.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to interact with Task Service.
 * Enables ProjectService to perform task-related operations remotely.
 */
@FeignClient(name = "task-service")
public interface TaskServiceClient {

    /**
     * Deletes all tasks associated with a specific project.
     * @param projectId the ID of the project whose tasks should be deleted
     */
    @DeleteMapping("/tasks/project/{projectId}")
    void deleteTasksByProjectId(@PathVariable("projectId") String projectId);
}
