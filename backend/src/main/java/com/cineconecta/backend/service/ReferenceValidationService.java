package com.cineconecta.backend.service;

import com.cineconecta.backend.model.relational.Profile;

/** Verifies in Oracle that profileId / subscriptionId / planId exist before writing to MongoDB. */
public interface ReferenceValidationService {

    /**
     * The profile, if it belongs to the account and is active (RNF2, RNF6, RF12).
     * Another account's profile, or one that does not exist: ForbiddenOperationException (403),
     * so the answer never reveals whether the id exists. Inactive: BusinessRuleException (422).
     */
    Profile requireActiveProfile(Integer accountId, Integer profileId);
}
