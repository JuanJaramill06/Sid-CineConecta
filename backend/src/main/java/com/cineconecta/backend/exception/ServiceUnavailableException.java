package com.cineconecta.backend.exception;

/** One of the databases is not available. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
