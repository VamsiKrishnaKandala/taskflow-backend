package com.taskflow.userservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Contains general application-wide beans.
 * We put PasswordEncoder here to break a circular dependency.
 */
@Configuration
@Slf4j
public class AppConfig {

    /**
     * Creates a PasswordEncoder bean for hashing and verifying passwords.
     *
     * @return A BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("Creating PasswordEncoder bean");
        return new BCryptPasswordEncoder();
    }
}