package com.taskflowpro.notificationservice.dto;

/**
 * Known event types that Notification Service expects.
 */
public enum EventType {
    TASK_CREATED,
    TASK_UPDATED,
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    
    PROJECT_CREATED,
    PROJECT_MEMBER_ADDED,
    PROJECT_MEMBER_REMOVED,
    PROJECT_UPDATED,
    PROJECT_DELETED,
    GENERIC
}