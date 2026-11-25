package com.cineconecta.backend.exception;

/**
 * Demo only: thrown after payment + subscription were inserted but before the commit, to
 * show that the transaction rolls both back (no partial records). Enabled with
 * DEMO_SIMULATE_FAILURES=true; see SubscriptionServiceImpl.
 */
public class SimulatedFailureException extends RuntimeException {
    public SimulatedFailureException(String message) {
        super(message);
    }
}
