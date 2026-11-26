package com.cineconecta.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cineconecta.backend.config.CorsConfig;
import com.cineconecta.backend.config.SecurityConfig;
import com.cineconecta.backend.config.WebConfig;
import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.exception.GlobalExceptionHandler;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.CsvExportService;
import com.cineconecta.backend.service.JwtService;
import com.cineconecta.backend.service.ReferenceValidationService;
import com.cineconecta.backend.service.ReportService;
import com.cineconecta.backend.service.impl.JwtServiceImpl;

/** /api/admin/reports needs ROLE_ADMIN, same as the rest of /api/admin/**. */
@WebMvcTest(controllers = AdminReportController.class)
@Import({ SecurityConfig.class, CorsConfig.class, WebConfig.class, GlobalExceptionHandler.class, JwtServiceImpl.class })
class AdminReportWebTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean ReportService reportService;
    @MockitoBean CsvExportService csvExportService;
    @MockitoBean ReferenceValidationService references;

    private String clientToken() {
        return "Bearer " + jwt.issue(new AuthenticatedUser("4", "x@example.com", "X", Role.CLIENT, null)).token();
    }

    private String adminToken() {
        return "Bearer " + jwt.issue(new AuthenticatedUser("64f0", "admin@cineconecta.com", "Admin", Role.ADMIN, null))
                .token();
    }

    @Test
    void reportsNeedAToken() throws Exception {
        mvc.perform(get("/api/admin/reports/income?from=2026-01-01&to=2026-01-31"))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(reportService);
    }

    @Test
    void aClientTokenCannotSeeReports() throws Exception {
        mvc.perform(get("/api/admin/reports/income?from=2026-01-01&to=2026-01-31")
                   .header(HttpHeaders.AUTHORIZATION, clientToken()))
           .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    void adminGetsJsonByDefault() throws Exception {
        when(reportService.incomeByPlan(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(new IncomeReportRow(1, "Básico", 10, new BigDecimal("149000.00"))));

        mvc.perform(get("/api/admin/reports/income?from=2026-01-01&to=2026-01-31")
                   .header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isOk());
    }

    @Test
    void adminCanRequestCsv() throws Exception {
        when(reportService.incomeByPlan(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());
        when(csvExportService.incomeToCsv(List.of())).thenReturn("planId,planName,subscriptionsCount,totalIncome\n");

        mvc.perform(get("/api/admin/reports/income?from=2026-01-01&to=2026-01-31&format=csv")
                   .header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }
}
