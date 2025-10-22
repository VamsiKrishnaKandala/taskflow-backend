package com.taskflow.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.taskflow.userservice.handler.UserHandler;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * Configures the functional routes for the User Service.
 * This class defines the API endpoints and maps them to handler methods.
 */
@Configuration
@Slf4j
public class RouterConfig {
	/**
     * Defines all the routes for the User service.
     *
     * @param userHandler The handler class containing the logic for each route.
     * @return A RouterFunction that Spring WebFlux will use to route requests.
     */
	@Bean
	public RouterFunction<ServerResponse> userRoutes(UserHandler userHandler){
		log.info("Configuring functional routes for UserHandler");
		
		//This is the functional equivalent of @RequestMapping
		return RouterFunctions
				.route()
				.POST("/users",
						accept(MediaType.APPLICATION_JSON),
						userHandler::handleRegisterUser)
				.POST("/auth/login",
						accept(MediaType.APPLICATION_JSON),
						userHandler::handleLoginUser)
				.GET("/users/{id}", 
                        userHandler::handleGetUserById)
				.POST("/auth/logout",
						userHandler::handleLogoutUser)
				.build();
	}
}
