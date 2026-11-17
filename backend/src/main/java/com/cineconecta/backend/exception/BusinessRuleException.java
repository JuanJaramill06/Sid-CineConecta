package com.cineconecta.backend.exception;

/** A business rule was violated (expired subscription, rating not allowed, ...). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
