package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.model.relational.Subscription;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.service.PlaybackAuthorization;
import com.cineconecta.backend.service.ReferenceValidationService;

/** RF40-44, RNF20. */
class PlaybackEligibilityServiceImplTest {

    private static final int ACCOUNT = 4;

    private final ReferenceValidationService references = mock(ReferenceValidationService.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final PlaybackEligibilityServiceImpl service =
            new PlaybackEligibilityServiceImpl(references, contents);

    private final Profile kidsProfile = mock(Profile.class);

    private static Content content(String id, int ageRating) {
        Content content = new Content();
        content.setId(id);
        content.setType(ContentType.MOVIE);
        content.setStatus(ContentStatus.PUBLISHED);
        content.setAgeRating(ageRating);
        return content;
    }

    @Test
    void noCurrentSubscriptionPropagates() {
        when(references.requireActiveSubscription(ACCOUNT)).thenThrow(new BusinessRuleException("no subscription"));

        assertThatThrownBy(() -> service.authorize(ACCOUNT, kidsProfile, "MOV-00001"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void contentNotPublishedIsForbidden() {
        Subscription subscription = mock(Subscription.class);
        when(references.requireActiveSubscription(ACCOUNT)).thenReturn(subscription);
        when(contents.findByIdAndStatus("MOV-99999", ContentStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(ACCOUNT, kidsProfile, "MOV-99999"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void ratingAboveTheProfileIsForbidden() {
        when(kidsProfile.getMaxAgeRating()).thenReturn(7);
        Subscription subscription = mock(Subscription.class);
        when(references.requireActiveSubscription(ACCOUNT)).thenReturn(subscription);
        when(contents.findByIdAndStatus("MOV-00002", ContentStatus.PUBLISHED))
                .thenReturn(Optional.of(content("MOV-00002", 18)));

        assertThatThrownBy(() -> service.authorize(ACCOUNT, kidsProfile, "MOV-00002"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void authorizedReturnsTheOracleSnapshot() {
        when(kidsProfile.getMaxAgeRating()).thenReturn(7);
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(3);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getSubscriptionId()).thenReturn(10);
        when(subscription.getPlan()).thenReturn(plan);
        when(references.requireActiveSubscription(ACCOUNT)).thenReturn(subscription);
        when(contents.findByIdAndStatus("MOV-00003", ContentStatus.PUBLISHED))
                .thenReturn(Optional.of(content("MOV-00003", 7)));

        PlaybackAuthorization auth = service.authorize(ACCOUNT, kidsProfile, "MOV-00003");

        assertThat(auth.content().getId()).isEqualTo("MOV-00003");
        assertThat(auth.subscriptionId()).isEqualTo(10);
        assertThat(auth.planId()).isEqualTo(3);
    }
}
