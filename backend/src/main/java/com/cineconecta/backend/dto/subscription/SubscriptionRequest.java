package com.cineconecta.backend.dto.subscription;

import jakarta.validation.constraints.NotNull;

/**
 * approvePayment simulates the payment gateway (RF18): false = rejected, nothing is
 * stored; true = sp_contract_subscription records payment + subscription together.
 */
public record SubscriptionRequest(@NotNull Integer planId, @NotNull Boolean approvePayment) {}
