package com.cineconecta.backend.service;

import com.cineconecta.backend.model.document.Content;

/** What PlaybackEligibilityService verifies: the content, plus the Oracle snapshot to save in the session (RF68). */
public record PlaybackAuthorization(Content content, Integer subscriptionId, Integer planId) {
}
