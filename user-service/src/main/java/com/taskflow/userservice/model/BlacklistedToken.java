package com.taskflow.userservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder; // Add Builder import
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient; // Import Transient
import org.springframework.data.domain.Persistable; // Import Persistable
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // Add Builder annotation
@Table("blacklisted_tokens")
// Make the class implement Persistable
public class BlacklistedToken implements Persistable<String> { // <-- IMPLEMENTS Persistable<String>

    @Id
    private String tokenSignature; // Matches column name

    private LocalDateTime expiry; // R2DBC maps LocalDateTime to TIMESTAMP
    
    @Transient // Tells Spring Data to ignore this field for DB mapping
    @Builder.Default // Ensures the builder initializes this field
    private boolean isNew = true; // Assume new by default
    
    @Override
    @Transient
    public String getId() {
    	return this.tokenSignature;
    }

    @Override
    @Transient // Mark this as transient too
    public boolean isNew() {
        return this.isNew;
    }

    // Optional: Add a setter if needed later for updates
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}