package com.taskflow.apigateway.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Represents a blacklisted token entity in the gateway context.
 * Only used for checking existence.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("blacklisted_tokens") // Must match the table name
public class BlacklistedToken {
    @Id
    private String tokenSignature; // Must match the primary key column name
    private LocalDateTime expiry; // Include expiry, though we don't use it here
}