package com.taskflowpro.projectservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO used for adding or removing members from a project.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMembersDTO {

    @NotEmpty(message = "Member IDs cannot be empty")
    private List<String> memberIds;
}
