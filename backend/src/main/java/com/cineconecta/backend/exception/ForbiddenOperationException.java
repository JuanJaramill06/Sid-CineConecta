package com.cineconecta.backend.exception;

/** The user is not allowed to perform this operation. */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
