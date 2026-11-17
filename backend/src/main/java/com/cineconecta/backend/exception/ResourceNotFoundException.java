package com.cineconecta.backend.exception;

/** A referenced resource does not exist. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
