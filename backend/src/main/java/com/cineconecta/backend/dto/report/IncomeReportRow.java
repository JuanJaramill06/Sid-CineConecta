package com.cineconecta.backend.dto.report;

import java.math.BigDecimal;

/** One row of the income-by-plan report (RF66): total collected and subscriptions in the period. */
public record IncomeReportRow(Integer planId, String planName, long subscriptionsCount, BigDecimal totalIncome) {
}
