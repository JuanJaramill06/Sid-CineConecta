package com.cineconecta.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.service.CsvExportService;

@Service
public class CsvExportServiceImpl implements CsvExportService {

    @Override
    public String incomeToCsv(List<IncomeReportRow> rows) {
        StringBuilder csv = new StringBuilder("planId,planName,subscriptionsCount,totalIncome\n");
        for (IncomeReportRow row : rows) {
            csv.append(row.planId()).append(',')
               .append(escape(row.planName())).append(',')
               .append(row.subscriptionsCount()).append(',')
               .append(row.totalIncome()).append('\n');
        }
        return csv.toString();
    }

    @Override
    public String consumptionToCsv(List<ConsumptionReportRow> rows) {
        StringBuilder csv = new StringBuilder("planId,planName,contentType,minutesWatched,distinctProfiles\n");
        for (ConsumptionReportRow row : rows) {
            csv.append(row.planId()).append(',')
               .append(escape(row.planName())).append(',')
               .append(row.contentType()).append(',')
               .append(row.minutesWatched()).append(',')
               .append(row.distinctProfiles()).append('\n');
        }
        return csv.toString();
    }

    // RFC 4180: quote a field that holds a comma, a quote or a newline; double any quote inside.
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
