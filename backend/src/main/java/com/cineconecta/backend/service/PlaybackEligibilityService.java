package com.cineconecta.backend.service;

import com.cineconecta.backend.model.relational.Profile;

/** Cross-database checks before playback (subscription, active profile, published content, age rating). */
public interface PlaybackEligibilityService {

    /**
     * RF40-44, RNF20. Oracle first (current subscription -> subscriptionId/planId; the profile
     * itself is already checked by @CurrentProfile), then Mongo (published, ageRating &lt;=
     * profile.maxAgeRating). Not published or above the profile's rating: ForbiddenOperationException
     * (403) — the same answer either way, so it never reveals which one failed. Either database
     * unreachable: propagates and GlobalExceptionHandler answers 503 (RNF12, never authorized by
     * default).
     */
    PlaybackAuthorization authorize(Integer accountId, Profile profile, String contentId);
}
