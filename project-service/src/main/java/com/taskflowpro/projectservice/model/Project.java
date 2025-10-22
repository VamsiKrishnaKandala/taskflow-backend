package com.taskflowpro.projectservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Table("projects")
public class Project implements Persistable<String> {

    @Id
    private String id;

    private String name;
    private String description;
    private LocalDate deadline;

    // Stored in DB as comma-separated values
    @JsonIgnore // prevent JSON deserialization into String
    private String memberIds;

    @JsonIgnore
    private String tags;

    // Exposed in API as array
    @Transient
    @JsonProperty("memberIds") // map incoming JSON array → this field
    private List<String> memberIdsList;

    @Transient
    @JsonProperty("tags")
    private List<String> tagsList;

    @Transient
    private boolean isNew = false; // tells R2DBC this is a new record

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    // Convert list fields to comma-separated strings before saving
    public void serializeLists() {
        if (memberIdsList != null && !memberIdsList.isEmpty()) {
            this.memberIds = String.join(",", memberIdsList);
        }
        if (tagsList != null && !tagsList.isEmpty()) {
            this.tags = String.join(",", tagsList);
        }
    }

    // Convert comma-separated strings back to lists when reading
    public void deserializeLists() {
        this.memberIdsList = (memberIds != null && !memberIds.isEmpty())
                ? Arrays.asList(memberIds.split(","))
                : new ArrayList<>(); // ensure non-null

        this.tagsList = (tags != null && !tags.isEmpty())
                ? Arrays.asList(tags.split(","))
                : new ArrayList<>(); // ensure non-null
    }
	
}
