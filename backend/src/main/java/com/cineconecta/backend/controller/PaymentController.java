package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.subscription.PaymentResponse;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Payments of the account in the token (RF5). */
    @GetMapping
    public List<PaymentResponse> history(@AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.history(user.accountId());
    }
}
