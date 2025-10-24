package com.taskflowpro.taskservice.dto;


import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * DTO for adding/removing assignees from a task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAssigneesDTO {

    @NotEmpty(message = "Assignee list cannot be empty")
    private List<String> assigneeIdsList;
}
