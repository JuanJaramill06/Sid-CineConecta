package com.cineconecta.backend.dto.subscription;

import java.math.BigDecimal;

/** price is in Colombian pesos (RF13). */
public record PlanResponse(Integer planId, String name, BigDecimal price, Integer maxProfiles) {}
