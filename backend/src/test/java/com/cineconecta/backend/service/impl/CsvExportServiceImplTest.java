package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.model.enums.ContentType;

class CsvExportServiceImplTest {

    private final CsvExportServiceImpl service = new CsvExportServiceImpl();

    @Test
    void incomeCsvHasAHeaderAndOneLinePerRow() {
        String csv = service.incomeToCsv(List.of(
                new IncomeReportRow(1, "Básico", 10, new BigDecimal("149000.00")),
                new IncomeReportRow(2, "Estándar", 5, new BigDecimal("124500.00"))));

        List<String> lines = csv.lines().toList();
        assertThat(lines.get(0)).isEqualTo("planId,planName,subscriptionsCount,totalIncome");
        assertThat(lines.get(1)).isEqualTo("1,Básico,10,149000.00");
        assertThat(lines.get(2)).isEqualTo("2,Estándar,5,124500.00");
    }

    @Test
    void aPlanNameWithACommaIsQuoted() {
        String csv = service.incomeToCsv(List.of(
                new IncomeReportRow(1, "Plan, especial", 1, new BigDecimal("1.00"))));

        assertThat(csv.lines().toList().get(1)).isEqualTo("1,\"Plan, especial\",1,1.00");
    }

    @Test
    void consumptionCsvHasAHeaderAndOneLinePerRow() {
        String csv = service.consumptionToCsv(List.of(
                new ConsumptionReportRow(2, "Estándar", ContentType.MOVIE, 2.1, 3)));

        List<String> lines = csv.lines().toList();
        assertThat(lines.get(0)).isEqualTo("planId,planName,contentType,minutesWatched,distinctProfiles");
        assertThat(lines.get(1)).isEqualTo("2,Estándar,MOVIE,2.1,3");
    }
}
