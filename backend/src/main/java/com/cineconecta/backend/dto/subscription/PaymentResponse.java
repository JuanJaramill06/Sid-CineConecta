package com.cineconecta.backend.dto.subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Integer paymentId, Integer subscriptionId, String planName,
                              LocalDateTime paidAt, BigDecimal amount, String reference) {}
