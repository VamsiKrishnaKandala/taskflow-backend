package com.taskflowpro.projectservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO used for adding or removing tags from a project.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTagsDTO {

    @NotEmpty(message = "Tags cannot be empty")
    private List<String> tags;
}
