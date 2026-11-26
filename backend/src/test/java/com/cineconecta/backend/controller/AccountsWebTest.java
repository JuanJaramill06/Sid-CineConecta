package com.cineconecta.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cineconecta.backend.config.CorsConfig;
import com.cineconecta.backend.config.SecurityConfig;
import com.cineconecta.backend.config.WebConfig;
import com.cineconecta.backend.dto.subscription.PlanResponse;
import com.cineconecta.backend.exception.GlobalExceptionHandler;
import com.cineconecta.backend.exception.PaymentRejectedException;
import com.cineconecta.backend.mapper.ProfileMapper;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.JwtService;
import com.cineconecta.backend.service.PaymentService;
import com.cineconecta.backend.service.PlanService;
import com.cineconecta.backend.service.ProfileService;
import com.cineconecta.backend.service.ReferenceValidationService;
import com.cineconecta.backend.service.SubscriptionService;
import com.cineconecta.backend.service.impl.JwtServiceImpl;

/** Package 2 routes: who may call them and that the account always comes from the token. */
@WebMvcTest(controllers = { PlanController.class, SubscriptionController.class,
                            PaymentController.class, ProfileController.class })
@Import({ SecurityConfig.class, CorsConfig.class, WebConfig.class, GlobalExceptionHandler.class, JwtServiceImpl.class })
class AccountsWebTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean PlanService planService;
    @MockitoBean SubscriptionService subscriptionService;
    @MockitoBean PaymentService paymentService;
    @MockitoBean ProfileService profileService;
    @MockitoBean ProfileMapper profileMapper;
    @MockitoBean ReferenceValidationService references;

    private String token(String accountId) {
        return "Bearer " + jwt.issue(new AuthenticatedUser(accountId, "x@example.com", "X", Role.CLIENT, null)).token();
    }

    @Test
    void plansArePublic() throws Exception {
        when(planService.list()).thenReturn(List.of(new PlanResponse(1, "Básico", new BigDecimal("14900.00"), 1)));

        mvc.perform(get("/api/plans"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Básico"));
    }

    @Test
    void contractingNeedsAToken() throws Exception {
        mvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"planId\":2,\"approvePayment\":true}"))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void contractUsesTheAccountOfTheTokenAndIgnoresAnyAccountInTheBody() throws Exception {
        mvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, token("4"))
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("{\"planId\":2,\"approvePayment\":true,\"accountId\":1}"))
           .andExpect(status().isCreated());
        verify(subscriptionService).contract(eq(4), any());
    }

    @Test
    void rejectedPaymentIs402() throws Exception {
        when(subscriptionService.contract(eq(4), any())).thenThrow(new PaymentRejectedException("El pago fue rechazado"));

        mvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, token("4"))
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("{\"planId\":2,\"approvePayment\":false}"))
           .andExpect(status().isPaymentRequired())
           .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));
    }

    @Test
    void contractWithoutPlanIs400() throws Exception {
        mvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, token("4"))
                   .contentType(MediaType.APPLICATION_JSON).content("{\"approvePayment\":true}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void profileOperationsAreScopedToTheTokenAccount() throws Exception {
        mvc.perform(post("/api/profiles/7/deactivate").header(HttpHeaders.AUTHORIZATION, token("1")))
           .andExpect(status().isOk());
        verify(profileService).deactivate(1, 7);
    }

    @Test
    void invalidProfileIs400() throws Exception {
        mvc.perform(post("/api/profiles").header(HttpHeaders.AUTHORIZATION, token("1"))
                   .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"maxAgeRating\":21}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void historyIsForTheTokenAccount() throws Exception {
        mvc.perform(get("/api/payments").header(HttpHeaders.AUTHORIZATION, token("6"))).andExpect(status().isOk());
        mvc.perform(get("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, token("6"))).andExpect(status().isOk());
        verify(paymentService).history(6);
        verify(subscriptionService).history(6);
    }
}
