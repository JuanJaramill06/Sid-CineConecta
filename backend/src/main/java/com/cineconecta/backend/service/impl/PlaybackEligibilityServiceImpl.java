package com.cineconecta.backend.service.impl;

import org.springframework.stereotype.Service;

import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.model.relational.Subscription;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.service.PlaybackAuthorization;
import com.cineconecta.backend.service.PlaybackEligibilityService;
import com.cineconecta.backend.service.ReferenceValidationService;

@Service
public class PlaybackEligibilityServiceImpl implements PlaybackEligibilityService {

    private static final String NOT_AVAILABLE = "El contenido no existe o no está disponible";

    private final ReferenceValidationService references;
    private final ContentRepository contents;

    public PlaybackEligibilityServiceImpl(ReferenceValidationService references, ContentRepository contents) {
        this.references = references;
        this.contents = contents;
    }

    @Override
    public PlaybackAuthorization authorize(Integer accountId, Profile profile, String contentId) {
        Subscription subscription = references.requireActiveSubscription(accountId);

        Content content = contents.findByIdAndStatus(contentId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ForbiddenOperationException(NOT_AVAILABLE));
        if (content.getAgeRating() > profile.getMaxAgeRating()) {
            throw new ForbiddenOperationException(NOT_AVAILABLE);
        }

        return new PlaybackAuthorization(content, subscription.getSubscriptionId(), subscription.getPlan().getPlanId());
    }
}
