package com.taskflowpro.projectservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO used for sending Project data in API responses.
 * - Exposes deserialized lists for members and tags.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponseDTO {

    private String id;

    private String name;

    private String description;

    private LocalDate deadline;

    private List<String> memberIds;

    private List<String> tags;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
