package com.taskflow.analyticsservice.service;

import com.taskflow.analyticsservice.dto.AnalyticsData;
import com.taskflow.analyticsservice.dto.AnalyticsSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for calculating analytics.
 */
public interface AnalyticsService {

    /**
     * Calculates project velocity metrics (tasks completed over time).
     */
    Flux<AnalyticsData> getProjectVelocity(String projectId, String authorizationHeader, String requesterId, String requesterRole);

    /**
     * Calculates a performance summary for a specific user.
     */
    Mono<AnalyticsSummary> getUserSummary(String userId, String authorizationHeader, String requesterId, String requesterRole);
}