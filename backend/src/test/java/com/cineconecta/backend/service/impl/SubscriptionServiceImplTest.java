package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.cineconecta.backend.dto.subscription.SubscriptionRequest;
import com.cineconecta.backend.dto.subscription.SubscriptionResponse;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.PaymentRejectedException;
import com.cineconecta.backend.exception.SimulatedFailureException;
import com.cineconecta.backend.mapper.SubscriptionMapper;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.model.relational.Subscription;
import com.cineconecta.backend.repository.relational.PaymentRepository;
import com.cineconecta.backend.repository.relational.SubscriptionProcedures;
import com.cineconecta.backend.repository.relational.SubscriptionRepository;
import com.cineconecta.backend.mapper.PaymentMapper;

/** RF16-RF20: what reaches sp_contract_subscription. Account 4 of the seed: the demo account. */
class SubscriptionServiceImplTest {

    private static final int ACCOUNT = 4;

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final SubscriptionProcedures procedures = mock(SubscriptionProcedures.class);
    private final PaymentServiceImpl payments = new PaymentServiceImpl(mock(PaymentRepository.class),
            Mappers.getMapper(PaymentMapper.class));

    private SubscriptionServiceImpl service(boolean failureSimulation) {
        return new SubscriptionServiceImpl(subscriptions, procedures, payments,
                Mappers.getMapper(SubscriptionMapper.class), failureSimulation);
    }

    @Test
    void rejectedPaymentNeverReachesTheDatabase() {
        assertThatThrownBy(() -> service(false).contract(ACCOUNT, new SubscriptionRequest(2, false, null)))
                .isInstanceOf(PaymentRejectedException.class);
        verify(procedures, never()).contract(anyInt(), anyInt(), anyString());
    }

    @Test
    void approvedPaymentCallsTheProcedureWithTheAccountFromTheToken() {
        Plan estandar = mock(Plan.class);
        when(estandar.getPlanId()).thenReturn(2);
        when(estandar.getName()).thenReturn("Estándar");
        Subscription created = mock(Subscription.class);
        when(created.getSubscriptionId()).thenReturn(12);
        when(created.getPlan()).thenReturn(estandar);
        when(created.getStartsAt()).thenReturn(LocalDateTime.now());
        when(created.isActiveAt(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(procedures.contract(eq(ACCOUNT), eq(2), startsWith("SIM-"))).thenReturn(12);
        when(subscriptions.findWithPlan(12)).thenReturn(Optional.of(created));

        SubscriptionResponse response = service(false).contract(ACCOUNT, new SubscriptionRequest(2, true, null));

        assertThat(response.subscriptionId()).isEqualTo(12);
        assertThat(response.planName()).isEqualTo("Estándar");
        assertThat(response.active()).isTrue();
    }

    @Test
    void simulatedFailureHappensAfterTheInsertsSoTheTransactionRollsBack() {
        when(procedures.contract(eq(ACCOUNT), eq(2), anyString())).thenReturn(13);

        assertThatThrownBy(() -> service(true).contract(ACCOUNT, new SubscriptionRequest(2, true, true)))
                .isInstanceOf(SimulatedFailureException.class)
                .hasMessageContaining("13");
        verify(procedures).contract(eq(ACCOUNT), eq(2), anyString());
    }

    @Test
    void simulatedFailureIsRefusedWhenTheDemoSwitchIsOff() {
        assertThatThrownBy(() -> service(false).contract(ACCOUNT, new SubscriptionRequest(2, true, true)))
                .isInstanceOf(BusinessRuleException.class);
        verify(procedures, never()).contract(anyInt(), anyInt(), anyString());
    }
}
