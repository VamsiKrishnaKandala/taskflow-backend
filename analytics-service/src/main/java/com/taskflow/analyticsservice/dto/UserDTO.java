package com.taskflow.analyticsservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Represents a response from the User Service
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDTO(
    String id,
    String name,
    String role
) {}