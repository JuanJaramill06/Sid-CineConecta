package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.subscription.SubscriptionRequest;
import com.cineconecta.backend.dto.subscription.SubscriptionResponse;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.service.SubscriptionService;

import jakarta.validation.Valid;

/** The account always comes from the token, never from the request (RNF2). */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public List<SubscriptionResponse> history(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptionService.history(user.accountId());
    }

    @GetMapping("/current")
    public SubscriptionResponse current(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptionService.current(user.accountId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse contract(@AuthenticationPrincipal AuthenticatedUser user,
                                         @Valid @RequestBody SubscriptionRequest request) {
        return subscriptionService.contract(user.accountId(), request);
    }
}
