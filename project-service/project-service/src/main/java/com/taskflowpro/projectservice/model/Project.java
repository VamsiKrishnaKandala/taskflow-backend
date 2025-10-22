package com.taskflowpro.projectservice.model;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a project entity with validation rules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("projects")
public class Project {

    @Id
    private String id; // Custom ID like PF-001

    @NotBlank(message = "Project name cannot be blank")
    private String name;

    private String description;

    @NotNull(message = "Deadline must be provided")
    @Future(message = "Deadline must be a future date")
    private LocalDate deadline;

    private List<String> memberIds;
    private List<String> tags;
}