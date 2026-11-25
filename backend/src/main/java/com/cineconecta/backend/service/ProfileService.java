package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.profile.ProfileRequest;
import com.cineconecta.backend.dto.profile.ProfileResponse;

/**
 * Create, edit, deactivate and list profiles (plan limit, unique name). Every method takes
 * the accountId from the token and only touches that account's profiles (RNF2): another
 * account's profile answers 403.
 */
public interface ProfileService {

    /** All profiles of the account, active and inactive (RF6). */
    List<ProfileResponse> list(Integer accountId);

    /**
     * RF7-RF10. Needs a current subscription (422) and a free slot in its plan (422).
     * Name unique within the account, any letter case (409). Concurrency-safe: locks the
     * account row first, so two simultaneous creations cannot exceed the limit.
     */
    ProfileResponse create(Integer accountId, ProfileRequest request);

    /** Change name or maxAgeRating. Same unique-name rule (409). */
    ProfileResponse update(Integer accountId, Integer profileId, ProfileRequest request);

    /** isActive = 'N'. Its sessions and My list are kept (RF11). Idempotent. */
    ProfileResponse deactivate(Integer accountId, Integer profileId);

    /** Back to active: same rules as create (subscription, plan limit, account lock). */
    ProfileResponse activate(Integer accountId, Integer profileId);
}
