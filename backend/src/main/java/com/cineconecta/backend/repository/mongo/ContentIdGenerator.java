package com.cineconecta.backend.repository.mongo;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.cineconecta.backend.model.document.Counter;
import com.cineconecta.backend.model.enums.ContentType;

/**
 * Atomic id generator for Content (RF63: ids are never reused). One counter document per
 * prefix (MOV/SER/DOC) in the "counters" collection (database/mongodb/01_collections.js),
 * incremented with findAndModify($inc, upsert) so two admins never get the same id.
 */
@Repository
public class ContentIdGenerator {

    private final MongoTemplate mongoTemplate;

    public ContentIdGenerator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String nextContentId(ContentType type) {
        String prefix = prefix(type);
        return "%s-%05d".formatted(prefix, nextSeq(prefix));
    }

    /** Deterministic, not from the counter: an episode's id derives from its series (docs §4). */
    public String episodeId(String seriesId, int seasonNumber, int episodeNumber) {
        return "%s-S%02dE%02d".formatted(seriesId, seasonNumber, episodeNumber);
    }

    private long nextSeq(String prefix) {
        Counter updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(prefix)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Counter.class);
        return updated.getSeq();
    }

    private String prefix(ContentType type) {
        return switch (type) {
            case MOVIE -> "MOV";
            case SERIES -> "SER";
            case DOCUMENTARY -> "DOC";
        };
    }
}
