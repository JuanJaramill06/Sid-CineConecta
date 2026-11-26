package com.cineconecta.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.service.CsvExportService;
import com.cineconecta.backend.service.ReportService;

/** Admin reports (RF66-71): date range + optional CSV export. Already restricted to ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;
    private final CsvExportService csvExportService;

    public AdminReportController(ReportService reportService, CsvExportService csvExportService) {
        this.reportService = reportService;
        this.csvExportService = csvExportService;
    }

    @GetMapping("/income")
    public ResponseEntity<?> income(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                    @RequestParam(required = false) String format) {
        List<IncomeReportRow> rows = reportService.incomeByPlan(from, to);
        if ("csv".equalsIgnoreCase(format)) {
            return csv(csvExportService.incomeToCsv(rows), "income.csv");
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/consumption")
    public ResponseEntity<?> consumption(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                         @RequestParam(required = false) String format) {
        List<ConsumptionReportRow> rows = reportService.consumptionByPlanAndType(from, to);
        if ("csv".equalsIgnoreCase(format)) {
            return csv(csvExportService.consumptionToCsv(rows), "consumption.csv");
        }
        return ResponseEntity.ok(rows);
    }

    private static ResponseEntity<String> csv(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
