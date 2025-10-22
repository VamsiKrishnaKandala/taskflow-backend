package com.taskflow.userservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.taskflow.userservice.model.BlacklistedToken;

@Repository
public interface BlacklistedTokenRepository extends ReactiveCrudRepository<BlacklistedToken	, String>{
	
}
