package com.cineconecta.backend.exception;

/** The request clashes with existing data: duplicate email, profile name or My list item (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
