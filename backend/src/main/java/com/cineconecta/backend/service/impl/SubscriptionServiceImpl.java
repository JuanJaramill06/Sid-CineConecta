package com.cineconecta.backend.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.dto.subscription.SubscriptionRequest;
import com.cineconecta.backend.dto.subscription.SubscriptionResponse;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ResourceNotFoundException;
import com.cineconecta.backend.exception.SimulatedFailureException;
import com.cineconecta.backend.mapper.SubscriptionMapper;
import com.cineconecta.backend.repository.relational.SubscriptionProcedures;
import com.cineconecta.backend.repository.relational.SubscriptionRepository;
import com.cineconecta.backend.service.PaymentService;
import com.cineconecta.backend.service.SubscriptionService;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionProcedures procedures;
    private final PaymentService paymentService;
    private final SubscriptionMapper mapper;
    private final boolean failureSimulationEnabled;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptions, SubscriptionProcedures procedures,
                                   PaymentService paymentService, SubscriptionMapper mapper,
                                   @Value("${cineconecta.demo.simulate-failures:false}") boolean failureSimulationEnabled) {
        this.subscriptions = subscriptions;
        this.procedures = procedures;
        this.paymentService = paymentService;
        this.mapper = mapper;
        this.failureSimulationEnabled = failureSimulationEnabled;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> history(Integer accountId) {
        return mapper.toResponses(subscriptions.findHistory(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse current(Integer accountId) {
        return subscriptions.findActive(accountId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no tiene una suscripción vigente"));
    }

    // One Oracle transaction: the procedure inserts subscription + payment without committing;
    // this method commits both on success and rolls both back on any exception (RF19, RNF11).
    @Override
    @Transactional
    public SubscriptionResponse contract(Integer accountId, SubscriptionRequest request) {
        boolean simulateFailure = Boolean.TRUE.equals(request.simulateFailure());
        if (simulateFailure && !failureSimulationEnabled) {
            throw new BusinessRuleException("La simulación de fallas está desactivada (DEMO_SIMULATE_FAILURES)");
        }

        // Rejected: exception here, before touching the database. Nothing is stored (RF18-RF20).
        String paymentReference = paymentService.authorize(request.approvePayment());

        Integer subscriptionId = procedures.contract(accountId, request.planId(), paymentReference);

        if (simulateFailure) {
            throw new SimulatedFailureException("Falla simulada después de insertar la suscripción "
                    + subscriptionId + " y su pago, antes del commit: la transacción se revirtió");
        }
        return subscriptions.findWithPlan(subscriptionId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new IllegalStateException("Subscription " + subscriptionId + " not found"));
    }
}
