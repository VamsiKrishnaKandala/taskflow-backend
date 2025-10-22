package com.taskflow.apigateway.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistedTokenRepository extends ReactiveCrudRepository<BlacklistedToken, String> {
    // findById(tokenSignature) is provided automatically
}