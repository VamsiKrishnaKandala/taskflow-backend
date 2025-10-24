package com.taskflowpro.taskservice.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating/updating a task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class TaskRequestDTO {

    
    @NotBlank(message = "projectId is required")
    private String projectId;
    
    @NotBlank(message = "Task title cannot be empty")
    private String title;
    
    private String description;
    
    private String status;
    
    private String priority;
    
    private List<String> assigneeIdsList;

    private List<String> tagsList;

    private LocalDate dueDate;
    private String createdBy;
    private LocalDateTime createdAt;
}