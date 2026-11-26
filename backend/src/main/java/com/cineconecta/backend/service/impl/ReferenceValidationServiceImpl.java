package com.cineconecta.backend.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.model.relational.Subscription;
import com.cineconecta.backend.repository.relational.ProfileRepository;
import com.cineconecta.backend.repository.relational.SubscriptionRepository;
import com.cineconecta.backend.service.ReferenceValidationService;

@Service
public class ReferenceValidationServiceImpl implements ReferenceValidationService {

    private final ProfileRepository profiles;
    private final SubscriptionRepository subscriptions;

    public ReferenceValidationServiceImpl(ProfileRepository profiles, SubscriptionRepository subscriptions) {
        this.profiles = profiles;
        this.subscriptions = subscriptions;
    }

    @Override
    @Transactional(readOnly = true)
    public Profile requireActiveProfile(Integer accountId, Integer profileId) {
        Profile profile = profiles.findByProfileIdAndAccount_AccountId(profileId, accountId)
                .orElseThrow(() -> new ForbiddenOperationException("El perfil no pertenece a la cuenta"));
        if (!Boolean.TRUE.equals(profile.getActive())) {
            throw new BusinessRuleException("El perfil está desactivado: actívelo o elija otro");
        }
        return profile;
    }

    @Override
    @Transactional(readOnly = true)
    public Subscription requireActiveSubscription(Integer accountId) {
        return subscriptions.findActive(accountId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Se necesita una suscripción vigente para esta operación"));
    }
}
