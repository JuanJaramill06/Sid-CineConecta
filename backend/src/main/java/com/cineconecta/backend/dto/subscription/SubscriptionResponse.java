package com.cineconecta.backend.dto.subscription;

import java.time.LocalDateTime;

/** Period is [startsAt, endsAt): the end instant is excluded (RF21). active = now inside it. */
public record SubscriptionResponse(Integer subscriptionId, Integer planId, String planName,
                                   LocalDateTime startsAt, LocalDateTime endsAt, boolean active) {}
