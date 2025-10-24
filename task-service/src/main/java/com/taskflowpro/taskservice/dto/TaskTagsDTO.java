package com.taskflowpro.taskservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * DTO for adding/removing tags from a task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskTagsDTO {

    @NotEmpty(message = "Tags list cannot be empty")
    private List<String> tagsList;
}