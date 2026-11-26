package com.cineconecta.backend.service.impl;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;

import com.cineconecta.backend.dto.playback.ContinueWatchingResponse;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.service.ContinueWatchingService;

/**
 * Port of database/mongodb/06_operations.js section 6, stage for stage, using raw
 * {@link Document} aggregation operations: the pipeline's $reduce/$let/$cond expressions are
 * not representable with Spring Data Mongo's typed Aggregation DSL, and this keeps exact
 * fidelity with that already-validated reference instead of reimplementing the logic.
 */
@Service
public class ContinueWatchingServiceImpl implements ContinueWatchingService {

    private static final int DEFAULT_LIMIT = 20;

    private final MongoTemplate mongoTemplate;

    public ContinueWatchingServiceImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ContinueWatchingResponse> list(Integer profileId) {
        List<AggregationOperation> stages = List.of(
                raw(new Document("$match", new Document("profileId", profileId))),
                raw(new Document("$sort", new Document("lastActivityAt", -1))),
                raw(new Document("$group", new Document("_id", "$contentId")
                        .append("s", new Document("$first", "$$ROOT")))),
                raw(new Document("$replaceWith", "$s")),
                raw(new Document("$lookup", new Document("from", "Content")
                        .append("localField", "contentId").append("foreignField", "_id").append("as", "c")
                        .append("pipeline", List.of(new Document("$project", new Document("title", 1)
                                .append("status", 1).append("type", 1).append("coverUrl", 1)
                                .append("episodes", new Document("$reduce", new Document("input",
                                        new Document("$ifNull", List.of("$series.seasons", List.of())))
                                        .append("initialValue", List.of())
                                        .append("in", new Document("$concatArrays",
                                                List.of("$$value", "$$this.episodes")))))))))),
                raw(new Document("$unwind", "$c")),
                raw(new Document("$match", new Document("c.status", "PUBLISHED"))),
                raw(new Document("$set", new Document("next", new Document("$let",
                        new Document("vars", new Document("i", new Document("$indexOfArray",
                                List.of("$c.episodes._id", "$episodeId"))))
                        .append("in", new Document("$arrayElemAt",
                                List.of("$c.episodes", new Document("$add", List.of("$$i", 1))))))))),
                raw(new Document("$match", new Document("$or", List.of(
                        new Document("completed", false),
                        new Document("c.type", "SERIES").append("next", new Document("$exists", true)))))),
                raw(new Document("$project", new Document("_id", 0)
                        .append("contentId", 1).append("title", "$c.title").append("type", "$c.type")
                        .append("coverUrl", "$c.coverUrl").append("lastActivityAt", 1)
                        .append("episodeId", new Document("$cond", List.of("$completed", "$next._id", "$episodeId")))
                        .append("positionSeconds", new Document("$cond", List.of("$completed", 0, "$lastPositionSeconds"))))),
                raw(new Document("$sort", new Document("lastActivityAt", -1))),
                raw(new Document("$limit", DEFAULT_LIMIT)));

        List<Document> results = mongoTemplate.aggregate(Aggregation.newAggregation(stages), "Watch_sessions",
                Document.class).getMappedResults();
        return results.stream().map(this::toResponse).toList();
    }

    private static AggregationOperation raw(Document stage) {
        return context -> stage;
    }

    private ContinueWatchingResponse toResponse(Document document) {
        String typeName = document.getString("type");
        Object lastActivity = document.get("lastActivityAt");
        Instant lastActivityAt = lastActivity instanceof java.util.Date date ? date.toInstant() : null;
        Object position = document.get("positionSeconds");
        Integer positionSeconds = position == null ? null : ((Number) position).intValue();
        return new ContinueWatchingResponse(
                document.getString("contentId"),
                document.getString("title"),
                typeName == null ? null : ContentType.valueOf(typeName),
                document.getString("coverUrl"),
                lastActivityAt,
                document.getString("episodeId"),
                positionSeconds);
    }
}
