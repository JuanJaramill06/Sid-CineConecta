package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.subscription.SubscriptionRequest;
import com.cineconecta.backend.dto.subscription.SubscriptionResponse;

/** Contract a subscription: approved payment + subscription in ONE Oracle transaction, no overlapping periods. */
public interface SubscriptionService {

    /** All subscriptions of the account, newest first; old ones are kept (RF4, RF17). */
    List<SubscriptionResponse> history(Integer accountId);

    /** The current subscription (RF21); ResourceNotFoundException (404) if there is none. */
    SubscriptionResponse current(Integer accountId);

    /**
     * RF16-RF23. Rejected payment: 402, nothing stored. Approved: sp_contract_subscription
     * inserts subscription + payment atomically. Overlap: 409 (-20001). More active profiles
     * than the new plan allows: 422 (-20002). Two simultaneous requests: the procedure's
     * account lock lets only one through.
     */
    SubscriptionResponse contract(Integer accountId, SubscriptionRequest request);
}
