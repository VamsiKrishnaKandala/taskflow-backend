package com.taskflow.analyticsservice.dto;

/**
 * Represents a summary of analytics metrics for a specific user or project.
 * Uses Java Record for concise, immutable data representation.
 *
 * @param targetId The ID of the user or project this summary is for.
 * @param totalTasksAssigned Total number of tasks ever assigned.
 * @param tasksCompleted Total number of tasks completed.
 * @param completionRate Percentage of assigned tasks completed (e.g., 0.85 for 85%).
 * @param averageTaskCompletionTime Average time to complete tasks (units TBD, e.g., hours).
 * @param overdueTasks Count of currently assigned tasks past their due date.
 */
public record AnalyticsSummary(
    String targetId,
    long totalTasksAssigned,
    long tasksCompleted,
    double completionRate,
    double averageTaskCompletionTime,
    long overdueTasks
) {}