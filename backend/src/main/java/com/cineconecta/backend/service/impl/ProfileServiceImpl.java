package com.cineconecta.backend.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.dto.profile.ProfileRequest;
import com.cineconecta.backend.dto.profile.ProfileResponse;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ConflictException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.exception.ResourceNotFoundException;
import com.cineconecta.backend.mapper.ProfileMapper;
import com.cineconecta.backend.model.relational.Account;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.relational.AccountRepository;
import com.cineconecta.backend.repository.relational.ProfileRepository;
import com.cineconecta.backend.repository.relational.SubscriptionRepository;
import com.cineconecta.backend.service.ProfileService;

@Service
public class ProfileServiceImpl implements ProfileService {

    private static final String DUPLICATE_NAME = "Ya existe un perfil con ese nombre en la cuenta";

    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final SubscriptionRepository subscriptions;
    private final ProfileMapper mapper;

    public ProfileServiceImpl(AccountRepository accounts, ProfileRepository profiles,
                              SubscriptionRepository subscriptions, ProfileMapper mapper) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.subscriptions = subscriptions;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileResponse> list(Integer accountId) {
        return mapper.toResponses(profiles.findByAccount_AccountIdOrderByProfileIdAsc(accountId));
    }

    @Override
    @Transactional
    public ProfileResponse create(Integer accountId, ProfileRequest request) {
        Account account = lockAccount(accountId);
        requireFreeSlot(accountId);
        String name = request.name().trim();
        requireUniqueName(accountId, name, null);
        Profile profile = new Profile(account, name, request.maxAgeRating());
        return mapper.toResponse(saveUnique(profile));
    }

    @Override
    @Transactional
    public ProfileResponse update(Integer accountId, Integer profileId, ProfileRequest request) {
        Profile profile = ownedProfile(accountId, profileId);
        String name = request.name().trim();
        requireUniqueName(accountId, name, profileId);
        profile.setName(name);
        profile.setMaxAgeRating(request.maxAgeRating());
        return mapper.toResponse(saveUnique(profile));
    }

    @Override
    @Transactional
    public ProfileResponse deactivate(Integer accountId, Integer profileId) {
        Profile profile = ownedProfile(accountId, profileId);
        profile.setActive(false); // never deleted: its activity in MongoDB stays valid (RF11)
        return mapper.toResponse(profile);
    }

    @Override
    @Transactional
    public ProfileResponse activate(Integer accountId, Integer profileId) {
        lockAccount(accountId);
        Profile profile = ownedProfile(accountId, profileId);
        if (Boolean.TRUE.equals(profile.getActive())) {
            return mapper.toResponse(profile);
        }
        requireFreeSlot(accountId);
        profile.setActive(true);
        return mapper.toResponse(profile);
    }

    // ---- Rules -----------------------------------------------------------------------------

    /**
     * SELECT ... FOR UPDATE on the account, before counting. A second request for the same
     * account waits here until the first commits, then counts the new profile. Without it
     * both would count the same free slot (05b_concurrency_demo.sql, case 2).
     */
    private Account lockAccount(Integer accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe"));
    }

    // RF9 + RF10: current subscription and active profiles < maxProfiles of its plan.
    private void requireFreeSlot(Integer accountId) {
        Plan plan = subscriptions.findActive(accountId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Se necesita una suscripción vigente para tener perfiles activos"))
                .getPlan();
        long active = profiles.countByAccount_AccountIdAndActive(accountId, Boolean.TRUE);
        if (active >= plan.getMaxProfiles()) {
            throw new BusinessRuleException("El plan " + plan.getName() + " admite " + plan.getMaxProfiles()
                    + " perfiles activos y ya hay " + active + ". Desactive uno o cambie de plan");
        }
    }

    // RF8. Friendly check first; the index ux_profiles_name_ci is the real guarantee.
    private void requireUniqueName(Integer accountId, String name, Integer exceptProfileId) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean taken = profiles.findByAccount_AccountIdOrderByProfileIdAsc(accountId).stream()
                .anyMatch(p -> !p.getProfileId().equals(exceptProfileId)
                        && p.getName().toLowerCase(Locale.ROOT).equals(lower));
        if (taken) {
            throw new ConflictException(DUPLICATE_NAME);
        }
    }

    private Profile saveUnique(Profile profile) {
        try {
            return profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_NAME);
        }
    }

    private Profile ownedProfile(Integer accountId, Integer profileId) {
        return profiles.findByProfileIdAndAccount_AccountId(profileId, accountId)
                .orElseThrow(() -> new ForbiddenOperationException("El perfil no pertenece a la cuenta"));
    }
}
