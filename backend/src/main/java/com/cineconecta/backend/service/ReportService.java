package com.cineconecta.backend.service;

import java.time.LocalDate;
import java.util.List;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;

/** Income by plan (Oracle) and consumption by plan and content type (Oracle + MongoDB). */
public interface ReportService {

    /** RF66: total collected and subscriptions acquired in [from, to], per plan. */
    List<IncomeReportRow> incomeByPlan(LocalDate from, LocalDate to);

    /**
     * RF67-69: minutes watched and distinct profiles in [from, to], per plan and content type.
     * Each session counts under the plan vigente when it started (RF68, already snapshotted in
     * WatchSession.planId); series are counted by episode (one session per episode).
     */
    List<ConsumptionReportRow> consumptionByPlanAndType(LocalDate from, LocalDate to);
}
