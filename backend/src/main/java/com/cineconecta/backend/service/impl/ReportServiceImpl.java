package com.cineconecta.backend.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.dto.report.ConsumptionReportRow;
import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.repository.relational.PaymentRepository;
import com.cineconecta.backend.repository.relational.PlanRepository;
import com.cineconecta.backend.service.ReportService;

/**
 * Consumption is a Java port of database/mongodb/06_operations.js section 7 (consumptionReport):
 * same $match/$group/$project, as raw Document stages for the same reason as
 * ContinueWatchingServiceImpl. Plan names come from Oracle, added in Java (comment in the script).
 */
@Service
public class ReportServiceImpl implements ReportService {

    private final PaymentRepository payments;
    private final PlanRepository plans;
    private final MongoTemplate mongoTemplate;

    public ReportServiceImpl(PaymentRepository payments, PlanRepository plans, MongoTemplate mongoTemplate) {
        this.payments = payments;
        this.plans = plans;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomeReportRow> incomeByPlan(LocalDate from, LocalDate to) {
        return payments.incomeByPlan(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    @Override
    public List<ConsumptionReportRow> consumptionByPlanAndType(LocalDate from, LocalDate to) {
        Date fromDate = toDate(from);
        Date toDateExclusive = toDate(to.plusDays(1));

        List<AggregationOperation> stages = List.of(
                raw(new Document("$match", new Document("startedAt",
                        new Document("$gte", fromDate).append("$lt", toDateExclusive)))),
                // Only the fields of ix_sessions_report and no _id: the query stays "covered"
                // (PROJECTION_COVERED, 0 documents read), the plan measured in 05_index_experiment.js.
                raw(new Document("$project", new Document("_id", 0).append("planId", 1)
                        .append("contentType", 1).append("profileId", 1).append("watchedSeconds", 1))),
                raw(new Document("$group", new Document("_id",
                        new Document("planId", "$planId").append("contentType", "$contentType"))
                        .append("watchedSeconds", new Document("$sum", "$watchedSeconds"))
                        .append("profiles", new Document("$addToSet", "$profileId")))),
                raw(new Document("$project", new Document("_id", 0)
                        .append("planId", "$_id.planId").append("contentType", "$_id.contentType")
                        .append("watchedSeconds", 1)
                        .append("distinctProfiles", new Document("$size", "$profiles")))),
                raw(new Document("$sort", new Document("planId", 1).append("contentType", 1))));

        List<Document> results = mongoTemplate.aggregate(Aggregation.newAggregation(stages), "Watch_sessions",
                Document.class).getMappedResults();

        Map<Integer, String> planNames = plans.findAll().stream()
                .collect(Collectors.toMap(Plan::getPlanId, Plan::getName));

        return results.stream().map(doc -> toRow(doc, planNames)).toList();
    }

    private static AggregationOperation raw(Document stage) {
        return context -> stage;
    }

    private static ConsumptionReportRow toRow(Document document, Map<Integer, String> planNames) {
        // $sum answers int32 or int64 depending on the total, so read every number as Number.
        Integer planId = number(document, "planId").intValue();
        long watchedSeconds = number(document, "watchedSeconds").longValue();
        long distinctProfiles = number(document, "distinctProfiles").longValue();
        return new ConsumptionReportRow(planId, planNames.get(planId),
                ContentType.valueOf(document.getString("contentType")), round1(watchedSeconds / 60.0),
                distinctProfiles);
    }

    private static Number number(Document document, String field) {
        Object value = document.get(field);
        return value instanceof Number n ? n : 0;
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
