package com.cineconecta.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.dto.subscription.PaymentResponse;
import com.cineconecta.backend.exception.PaymentRejectedException;
import com.cineconecta.backend.mapper.PaymentMapper;
import com.cineconecta.backend.repository.relational.PaymentRepository;
import com.cineconecta.backend.service.PaymentService;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository payments;
    private final PaymentMapper mapper;

    public PaymentServiceImpl(PaymentRepository payments, PaymentMapper mapper) {
        this.payments = payments;
        this.mapper = mapper;
    }

    @Override
    public String authorize(boolean approved) {
        if (!approved) {
            throw new PaymentRejectedException("El pago fue rechazado: no se registró ninguna suscripción");
        }
        // PAYMENTS.reference is UNIQUE (uq_payments_reference); a UUID never repeats.
        return "SIM-" + UUID.randomUUID();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> history(Integer accountId) {
        return mapper.toResponses(payments.findHistory(accountId));
    }
}
