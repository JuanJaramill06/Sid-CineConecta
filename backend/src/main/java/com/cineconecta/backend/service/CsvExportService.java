package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;

/** CSV export of the reports (RF66, RF71). */
public interface CsvExportService {

    String incomeToCsv(List<IncomeReportRow> rows);

    String consumptionToCsv(List<ConsumptionReportRow> rows);
}
