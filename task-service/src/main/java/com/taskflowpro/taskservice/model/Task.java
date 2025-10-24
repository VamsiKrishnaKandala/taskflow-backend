package com.taskflowpro.taskservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Task entity stored in "tasks" table.
 * - Some fields (assigneeIds, tags) are stored in DB as comma-separated strings.
 * - Exposed in API as arrays via transient list fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("tasks")
public class Task implements Persistable<String> {

    @Id
    private String id;

    private String projectId; // FK to project (string id)
    private String title;
    private String description;
    private String status;    // TODO | IN_PROGRESS | REVIEW | DONE
    private String priority;  // LOW | MEDIUM | HIGH

    // Stored in DB as comma-separated values
    @JsonIgnore
    private String assigneeIds; // stored as comma-separated

    @JsonIgnore
    private String tags;        // stored as comma-separated

    @Transient
    @JsonProperty("assigneeIds")
    private List<String> assigneeIdsList;

    @Transient
    @JsonProperty("tags")
    private List<String> tagsList;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDate dueDate;

    @Transient
    private boolean isNew = false;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    // ---------------------------------------------------------------------
    // Serialization Helpers
    // ---------------------------------------------------------------------

    /**
     * Convert list fields to comma-separated strings before saving to DB.
     */
    public void serializeLists() {
        if (assigneeIdsList != null && !assigneeIdsList.isEmpty()) {
            this.assigneeIds = String.join(",", assigneeIdsList);
        }
        if (tagsList != null && !tagsList.isEmpty()) {
            this.tags = String.join(",", tagsList);
        }
    }

    /**
     * Convert comma-separated DB strings into lists after reading from DB.
     * Ensures lists are non-null to avoid NPE in handlers/tests.
     */
    public void deserializeLists() {
        this.assigneeIdsList = (assigneeIds != null && !assigneeIds.isEmpty())
                ? new ArrayList<>(Arrays.asList(assigneeIds.split(",")))
                : new ArrayList<>();
        this.tagsList = (tags != null && !tags.isEmpty())
                ? new ArrayList<>(Arrays.asList(tags.split(",")))
                : new ArrayList<>();
    }

    // ---------------------------------------------------------------------
    // Utility methods for Assignees and Tags
    // ---------------------------------------------------------------------

    /**
     * Adds new assignees to the task, avoiding duplicates.
     */
    public List<String> addAssignees(List<String> newAssignees) {
        if (assigneeIdsList == null) {
            assigneeIdsList = new ArrayList<>();
        }
        for (String id : newAssignees) {
            if (!assigneeIdsList.contains(id)) {
                assigneeIdsList.add(id);
            }
        }
        serializeLists(); // keep DB field in sync
        return assigneeIdsList;
    }

    /**
     * Removes specific assignees from the task.
     */
    public void removeAssignees(List<String> assigneesToRemove) {
        if (assigneeIdsList != null && assigneesToRemove != null) {
            assigneeIdsList.removeAll(assigneesToRemove);
            serializeLists();
        }
    }

    /**
     * Adds new tags to the task, avoiding duplicates.
     */
    public void addTags(List<String> newTags) {
        if (tagsList == null) {
            tagsList = new ArrayList<>();
        }
        for (String tag : newTags) {
            if (!tagsList.contains(tag)) {
                tagsList.add(tag);
            }
        }
        serializeLists();
    }

    /**
     * Removes specified tags from the task.
     */
    public void removeTags(List<String> tagsToRemove) {
        if (tagsList != null && tagsToRemove != null) {
            tagsList.removeAll(tagsToRemove);
            serializeLists();
        }
    }
}
