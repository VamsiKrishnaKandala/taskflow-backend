package com.taskflowpro.taskservice.dto;

import com.taskflowpro.taskservice.model.Task;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for returning Task details in API responses.
 * Used across handlers, services, and Feign responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponseDTO {

    private String id;
    private String projectId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private List<String> assigneeIdsList;
    private List<String> tagsList;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDate dueDate;

    /**
     * Converts a Task entity into a TaskResponseDTO.
     *
     * @param task The Task entity to convert.
     * @return A mapped TaskResponseDTO.
     */
    public static TaskResponseDTO fromEntity(Task task) {
        if (task == null) {
            return null;
        }

        return TaskResponseDTO.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .assigneeIdsList(task.getAssigneeIdsList())
                .tagsList(task.getTagsList())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .dueDate(task.getDueDate())
                .build();
    }
}
