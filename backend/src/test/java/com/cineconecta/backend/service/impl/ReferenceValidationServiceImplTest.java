package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.relational.ProfileRepository;

/** RNF2 / RF12 with the seed data: profile 16 is account 7's inactive "Hermano". */
class ReferenceValidationServiceImplTest {

    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final ReferenceValidationServiceImpl service = new ReferenceValidationServiceImpl(profiles);

    private static Profile profile(boolean active) {
        Profile p = mock(Profile.class);
        when(p.getActive()).thenReturn(active);
        return p;
    }

    @Test
    void ownActiveProfileIsReturned() {
        Profile juan = profile(true);
        when(profiles.findByProfileIdAndAccount_AccountId(1, 1)).thenReturn(Optional.of(juan));

        assertThat(service.requireActiveProfile(1, 1)).isSameAs(juan);
    }

    @Test
    void profileOfAnotherAccountIsForbidden() {
        when(profiles.findByProfileIdAndAccount_AccountId(4, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveProfile(1, 4)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void inactiveProfileCannotBeSelected() {
        Profile hermano = profile(false);
        when(profiles.findByProfileIdAndAccount_AccountId(16, 7)).thenReturn(Optional.of(hermano));

        assertThatThrownBy(() -> service.requireActiveProfile(7, 16)).isInstanceOf(BusinessRuleException.class);
    }
}
