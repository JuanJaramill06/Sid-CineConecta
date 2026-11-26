package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.cineconecta.backend.dto.profile.ProfileRequest;
import com.cineconecta.backend.dto.profile.ProfileResponse;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ConflictException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.mapper.ProfileMapper;
import com.cineconecta.backend.model.relational.Account;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.model.relational.Subscription;
import com.cineconecta.backend.repository.relational.AccountRepository;
import com.cineconecta.backend.repository.relational.ProfileRepository;
import com.cineconecta.backend.repository.relational.SubscriptionRepository;

/** RF7-RF12 with mocks of the Oracle repositories. Account 2 of the seed: Estándar (max 2). */
class ProfileServiceImplTest {

    private static final int ACCOUNT = 2;

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final ProfileServiceImpl service = new ProfileServiceImpl(accounts, profiles, subscriptions,
            Mappers.getMapper(ProfileMapper.class));

    private final Account account = mock(Account.class);

    @BeforeEach
    void accountExistsWithEstandarPlan() {
        when(accounts.findByIdForUpdate(ACCOUNT)).thenReturn(Optional.of(account));
        Plan estandar = mock(Plan.class);
        when(estandar.getName()).thenReturn("Estándar");
        when(estandar.getMaxProfiles()).thenReturn(2);
        Subscription current = mock(Subscription.class);
        when(current.getPlan()).thenReturn(estandar);
        when(subscriptions.findActive(ACCOUNT)).thenReturn(Optional.of(current));
        when(profiles.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Profile existing(int id, String name, boolean active) {
        Profile p = mock(Profile.class);
        when(p.getProfileId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getActive()).thenReturn(active);
        return p;
    }

    @Test
    void createsProfileWhenThereIsAFreeSlot() {
        when(profiles.countByAccount_AccountIdAndActive(ACCOUNT, true)).thenReturn(1L);
        Profile maria = existing(4, "María", true);
        when(profiles.findByAccount_AccountIdOrderByProfileIdAsc(ACCOUNT)).thenReturn(List.of(maria));

        ProfileResponse created = service.create(ACCOUNT, new ProfileRequest("  Invitado ", 12));

        assertThat(created.name()).isEqualTo("Invitado");
        assertThat(created.maxAgeRating()).isEqualTo(12);
        assertThat(created.active()).isTrue();
    }

    @Test
    void locksTheAccountBeforeCountingActiveProfiles() {
        when(profiles.countByAccount_AccountIdAndActive(ACCOUNT, true)).thenReturn(1L);
        when(profiles.findByAccount_AccountIdOrderByProfileIdAsc(ACCOUNT)).thenReturn(List.of());

        service.create(ACCOUNT, new ProfileRequest("Nuevo", 18));

        InOrder order = inOrder(accounts, profiles);
        order.verify(accounts).findByIdForUpdate(ACCOUNT);
        order.verify(profiles).countByAccount_AccountIdAndActive(ACCOUNT, true);
        order.verify(profiles).saveAndFlush(any());
    }

    @Test
    void rejectsWhenThePlanLimitIsReached() {
        when(profiles.countByAccount_AccountIdAndActive(ACCOUNT, true)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(ACCOUNT, new ProfileRequest("Tercero", 7)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("admite 2");
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWithoutACurrentSubscription() {
        when(subscriptions.findActive(ACCOUNT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ACCOUNT, new ProfileRequest("Nuevo", 18)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("suscripción vigente");
    }

    @Test
    void rejectsADuplicateNameIgnoringCase() {
        when(profiles.countByAccount_AccountIdAndActive(ACCOUNT, true)).thenReturn(1L);
        Profile kids = existing(5, "Kids", true);
        when(profiles.findByAccount_AccountIdOrderByProfileIdAsc(ACCOUNT)).thenReturn(List.of(kids));

        assertThatThrownBy(() -> service.create(ACCOUNT, new ProfileRequest("KIDS", 7)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void uniqueIndexViolationIsAlsoAConflict() {
        when(profiles.countByAccount_AccountIdAndActive(ACCOUNT, true)).thenReturn(1L);
        when(profiles.findByAccount_AccountIdOrderByProfileIdAsc(ACCOUNT)).thenReturn(List.of());
        when(profiles.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ORA-00001"));

        assertThatThrownBy(() -> service.create(ACCOUNT, new ProfileRequest("Kids", 7)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotTouchAProfileOfAnotherAccount() {
        when(profiles.findByProfileIdAndAccount_AccountId(1, ACCOUNT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(ACCOUNT, 1)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.update(ACCOUNT, 1, new ProfileRequest("X", 7)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void deactivateKeepsTheProfile() {
        Profile kids = existing(5, "Kids", true);
        when(profiles.findByProfileIdAndAccount_AccountId(5, ACCOUNT)).thenReturn(Optional.of(kids));

        service.deactivate(ACCOUNT, 5);

        verify(kids).setActive(false);
        verify(profiles, never()).delete(any());
    }

    @Test
    void reactivatingNeedsAFreeSlotAndTheLock() {
        Profile old = existing(9, "Antiguo", false);
        when(profiles.findByProfileIdAndAccount_AccountId(9, ACCOUNT)).thenReturn(Optional.of(old));
        when(profiles.countByAccount_AccountIdAndActive(ACCOUNT, true)).thenReturn(2L);

        assertThatThrownBy(() -> service.activate(ACCOUNT, 9)).isInstanceOf(BusinessRuleException.class);
        verify(accounts).findByIdForUpdate(ACCOUNT);
        verify(old, never()).setActive(true);
    }
}
