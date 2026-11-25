package com.cineconecta.backend.dto.subscription;

import jakarta.validation.constraints.NotNull;

/**
 * approvePayment simulates the payment gateway (RF18): false = rejected, nothing is
 * stored; true = sp_contract_subscription records payment + subscription together.
 * simulateFailure (optional, demo only): fail after both inserts and before the commit,
 * to show the rollback. Only honored when DEMO_SIMULATE_FAILURES=true.
 */
public record SubscriptionRequest(@NotNull Integer planId, @NotNull Boolean approvePayment,
                                  Boolean simulateFailure) {}
