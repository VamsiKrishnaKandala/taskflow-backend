package com.taskflow.userservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.taskflow.userservice.model.User;

import reactor.core.publisher.Mono;

/**
 * Repository interface for User entities.
 * Manages reactive data access to the 'users' table.
 */
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, String>{
	/**
     * Finds a user by their email address.
     *
     * @param email The email to search for.
     * @return A Mono emitting the found User, or empty if not found.
     */
	Mono<User> findByEmail(String email);
}
