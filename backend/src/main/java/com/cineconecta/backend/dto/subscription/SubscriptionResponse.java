package com.cineconecta.backend.dto.subscription;

import java.time.LocalDateTime;

/** Period is [startsAt, endsAt): the end instant is excluded (RF21). */
public record SubscriptionResponse(Integer subscriptionId, Integer planId, String planName,
                                   LocalDateTime startsAt, LocalDateTime endsAt) {}
