package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.subscription.PaymentResponse;

/** Simulated payment (approved / rejected) and payment history. */
public interface PaymentService {

    /**
     * Simulated gateway (RF18). Approved: returns a new unique payment reference.
     * Rejected: throws PaymentRejectedException and nothing is stored (rejected attempts
     * are not required to be kept).
     */
    String authorize(boolean approved);

    /** Payments of the account, newest first (RF5). */
    List<PaymentResponse> history(Integer accountId);
}
