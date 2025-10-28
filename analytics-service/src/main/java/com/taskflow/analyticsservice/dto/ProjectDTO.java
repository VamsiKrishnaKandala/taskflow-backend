package com.taskflow.analyticsservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents the data we care about from the Project Service.
 * Ignores any fields we don't need for analytics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDTO(
    String id,
    List<String> memberIds
) {}