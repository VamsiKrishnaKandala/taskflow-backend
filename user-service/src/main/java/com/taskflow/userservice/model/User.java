package com.taskflow.userservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient; // Import Transient
import org.springframework.data.domain.Persistable; // Import Persistable
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
// Make the class implement Persistable
public class User implements Persistable<String> { // <-- IMPLEMENTS Persistable<String>

    @Id
    private String id;

    private String name;

    private String email;

    private String password;

    private Role role;

    private String activeProjectIds;

    // --- ADD THESE ---
    @Transient // Tells Spring Data to ignore this field for DB mapping
    @Builder.Default // Ensures the builder initializes this field
    private boolean isNew = true; // Assume new by default

    @Override
    @Transient // Mark this as transient too
    public boolean isNew() {
        return this.isNew;
    }
    // --- END OF ADDITION ---

    // Optional: Add a setter if needed later for updates
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}