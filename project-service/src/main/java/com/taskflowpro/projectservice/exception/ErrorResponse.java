package com.taskflowpro.projectservice.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Standard structure for all error responses sent to the client.
 */
@Data
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}