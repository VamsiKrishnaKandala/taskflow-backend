package com.taskflow.analyticsservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents the data we care about from the Task Service.
 * Ignores any fields we don't need for analytics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDTO(
    String id,
    String projectId,
    String status,
    List<String> assigneeIdsList,
    LocalDateTime createdAt,
    LocalDate dueDate
    // We add @JsonIgnoreProperties to safely ignore description, title, etc.
) {}