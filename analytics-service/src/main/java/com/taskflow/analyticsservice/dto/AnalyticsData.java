package com.taskflow.analyticsservice.dto;

import java.time.LocalDate;

/**
 * Represents a single data point for analytics,
 * typically part of a time-series (e.g., for project velocity).
 * Uses Java Record for concise, immutable data representation.
 *
 * @param date The date or period this data point refers to.
 * @param value The calculated metric value (e.g., tasks completed).
 * @param metricName A descriptor for the metric (e.g., "tasksCompletedPerDay").
 */
public record AnalyticsData(
    LocalDate date,
    double value,
    String metricName
) {}