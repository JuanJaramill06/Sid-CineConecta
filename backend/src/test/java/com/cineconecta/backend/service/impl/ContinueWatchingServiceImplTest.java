package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import com.cineconecta.backend.dto.playback.ContinueWatchingResponse;
import com.cineconecta.backend.model.enums.ContentType;

/**
 * The pipeline itself (database/mongodb/06_operations.js section 6) is already validated there
 * against real data; this only checks that a Document the pipeline could produce maps correctly
 * to ContinueWatchingResponse. There is no embedded MongoDB in this project to exercise the
 * aggregation end to end (see MongoMappingIT, which needs a real Atlas connection instead).
 */
class ContinueWatchingServiceImplTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ContinueWatchingServiceImpl service = new ContinueWatchingServiceImpl(mongoTemplate);

    @Test
    void mapsAPipelineDocumentToTheResponse() {
        Date now = new Date();
        Document document = new Document()
                .append("contentId", "SER-00002").append("title", "Frontera Norte")
                .append("type", "SERIES").append("coverUrl", "https://img/cover.jpg")
                .append("lastActivityAt", now).append("episodeId", "SER-00002-S02E01")
                .append("positionSeconds", 0);
        AggregationResults<Document> results = new AggregationResults<>(List.of(document), new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("Watch_sessions"), eq(Document.class)))
                .thenReturn(results);

        List<ContinueWatchingResponse> list = service.list(4);

        assertThat(list).hasSize(1);
        ContinueWatchingResponse response = list.get(0);
        assertThat(response.contentId()).isEqualTo("SER-00002");
        assertThat(response.type()).isEqualTo(ContentType.SERIES);
        assertThat(response.episodeId()).isEqualTo("SER-00002-S02E01");
        assertThat(response.positionSeconds()).isZero();
        assertThat(response.lastActivityAt()).isEqualTo(now.toInstant());
    }
}
