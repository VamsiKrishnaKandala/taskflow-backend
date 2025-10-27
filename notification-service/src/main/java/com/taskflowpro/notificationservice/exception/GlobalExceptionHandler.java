package com.taskflowpro.notificationservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Minimal global exception component for mapping known exceptions to ServerResponse.
 * Your RouterFunctions can call this in .onError(...) if you want.
 */
@Component
@Slf4j
public class GlobalExceptionHandler {

    public Mono<ServerResponse> handleGenericError(ServerRequest req, Throwable ex) {
        log.error("Unhandled exception on {}: {}", req.path(), ex.getMessage(), ex);
        var body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal Server Error",
                "message", ex.getMessage(),
                "path", req.path()
        );
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    public Mono<ServerResponse> badRequest(ServerRequest req, String message) {
        var body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", message,
                "path", req.path()
        );
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }
}
