package com.taskflowpro.projectservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO used for creating or updating a Project.
 * - Exposed to API clients for request payloads.
 * - Validation annotations ensure input correctness.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRequestDTO {

    @NotBlank(message = "Project name cannot be empty")
    private String name;

    private String description;

    private LocalDate deadline;

    private List<String> memberIds; // Optional: member IDs for create/update

    private List<String> tags;      // Optional: tags for create/update
}
