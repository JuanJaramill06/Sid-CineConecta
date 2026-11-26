package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.repository.relational.PaymentRepository;
import com.cineconecta.backend.repository.relational.PlanRepository;

/** RF66-69: date range translation and the Mongo-aggregation-to-row mapping. */
class ReportServiceImplTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReportServiceImpl service = new ReportServiceImpl(payments, plans, mongoTemplate);

    @Test
    void incomeUsesAHalfOpenDateRange() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<IncomeReportRow> expected = List.of(new IncomeReportRow(2, "Estándar", 5, new BigDecimal("124500.00")));
        when(payments.incomeByPlan(from.atStartOfDay(), to.plusDays(1).atStartOfDay())).thenReturn(expected);

        List<IncomeReportRow> result = service.incomeByPlan(from, to);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void consumptionMergesPlanNamesAndRoundsMinutes() {
        Document row = new Document("planId", 2).append("contentType", "MOVIE")
                .append("watchedSeconds", 125).append("distinctProfiles", 3);
        AggregationResults<Document> results = new AggregationResults<>(List.of(row), new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("Watch_sessions"), eq(Document.class)))
                .thenReturn(results);
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(2);
        when(plan.getName()).thenReturn("Estándar");
        when(plans.findAll()).thenReturn(List.of(plan));

        List<ConsumptionReportRow> result = service.consumptionByPlanAndType(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(result).hasSize(1);
        ConsumptionReportRow r = result.get(0);
        assertThat(r.planId()).isEqualTo(2);
        assertThat(r.planName()).isEqualTo("Estándar");
        assertThat(r.contentType()).isEqualTo(ContentType.MOVIE);
        assertThat(r.minutesWatched()).isEqualTo(2.1); // 125s / 60 rounded to 1 decimal
        assertThat(r.distinctProfiles()).isEqualTo(3);
    }

    @Test
    void consumptionQueriesTheWatchSessionsCollection() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("Watch_sessions"), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));
        when(plans.findAll()).thenReturn(List.of());

        service.consumptionByPlanAndType(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        verify(mongoTemplate).aggregate(any(Aggregation.class), eq("Watch_sessions"), eq(Document.class));
    }

    @Test
    void largeTotalsArriveAsLongAndAreStillRead() {
        // $sum switches to int64 above 2^31: 3,000,000,000 s would break getInteger()
        Document row = new Document("planId", 3).append("contentType", "SERIES")
                .append("watchedSeconds", 3_000_000_000L).append("distinctProfiles", 25_000);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("Watch_sessions"), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(row), new Document()));
        when(plans.findAll()).thenReturn(List.of());

        ConsumptionReportRow r = service.consumptionByPlanAndType(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).get(0);

        assertThat(r.minutesWatched()).isEqualTo(50_000_000.0);
        assertThat(r.distinctProfiles()).isEqualTo(25_000);
    }

    @Test
    void consumptionProjectsOnlyTheIndexedFieldsSoTheQueryIsCovered() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("Watch_sessions"), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));
        when(plans.findAll()).thenReturn(List.of());

        service.consumptionByPlanAndType(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(aggregation.capture(), eq("Watch_sessions"), eq(Document.class));
        List<Document> stages = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        assertThat(stages.get(0)).containsKey("$match");
        assertThat(stages.get(1).get("$project", Document.class))
                .containsOnlyKeys("_id", "planId", "contentType", "profileId", "watchedSeconds");
    }
}
