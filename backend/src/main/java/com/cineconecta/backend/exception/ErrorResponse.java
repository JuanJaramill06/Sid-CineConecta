package com.cineconecta.backend.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Body of every error answer. details lists field errors (400) and is omitted otherwise. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, Instant timestamp, List<String> details) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), List.of());
    }
}
