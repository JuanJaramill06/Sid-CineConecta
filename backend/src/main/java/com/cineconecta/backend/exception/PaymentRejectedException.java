package com.cineconecta.backend.exception;

/** The simulated payment was rejected: nothing is stored (RF18, RF19). Answered with 402. */
public class PaymentRejectedException extends RuntimeException {
    public PaymentRejectedException(String message) {
        super(message);
    }
}
