package com.cineconecta.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.cineconecta.backend.config.MongoConfig;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.document.Episode;
import com.cineconecta.backend.model.document.Season;
import com.cineconecta.backend.model.document.SeriesDetails;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.repository.mongo.AdminUserRepository;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.repository.mongo.WatchSessionRepository;

/**
 * Integration test against the real (seeded) MongoDB: proves the Java mapping passes the
 * $jsonSchema validators. Loads only MongoDB (no JPA/Oracle). Run with MONGO_URI set:
 * .\mvnw.cmd test -Dtest=MongoMappingIT
 */
@DataMongoTest
@Import(MongoConfig.class) // auditing is not part of the @DataMongoTest slice
@EnabledIfEnvironmentVariable(named = "MONGO_URI", matches = ".+")
class MongoMappingIT {

    private static final String TEST_ID = "SER-99999"; // matches the id pattern, outside seed/volume ranges

    @Autowired ContentRepository contents;
    @Autowired WatchSessionRepository sessions;
    @Autowired AdminUserRepository admins;

    @Test
    void connectsToSeededDatabaseNotLocalhost() {
        assertThat(contents.count()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void seriesRoundTripPassesValidator() {
        contents.deleteById(TEST_ID); // leftover from an interrupted run
        try {
            contents.save(series());

            Content read = contents.findById(TEST_ID).orElseThrow();
            assertThat(read.getType()).isEqualTo(ContentType.SERIES);
            assertThat(read.getAgeRating()).isEqualTo(12);
            assertThat(read.getCreatedAt()).isNotNull();
            assertThat(read.isNew()).isFalse();
            Episode ep = read.getSeries().getSeasons().get(0).getEpisodes().get(0);
            assertThat(ep.getId()).isEqualTo(TEST_ID + "-S01E01");
            assertThat(ep.getDurationSeconds()).isEqualTo(1800);

            read.setTitle("Serie de prueba (editada)"); // existing document -> update, not insert
            contents.save(read);
            assertThat(contents.findById(TEST_ID).orElseThrow().getTitle()).endsWith("(editada)");
        } finally {
            contents.deleteById(TEST_ID);
        }
    }

    @Test
    void readsSeededAdminAndSession() {
        assertThat(admins.findByEmail("admin@cineconecta.com")).isPresent();
        assertThat(sessions.findAll(PageRequest.of(0, 1))).isNotEmpty();
    }

    private static Content series() {
        Episode ep = new Episode();
        ep.setId(TEST_ID + "-S01E01");
        ep.setNumber(1);
        ep.setTitle("Piloto");
        ep.setSynopsis("Episodio de prueba.");
        ep.setDurationSeconds(1800);

        Season season = new Season();
        season.setSeasonNumber(1);
        season.setDescription("Temporada de prueba");
        season.setEpisodes(List.of(ep));

        SeriesDetails details = new SeriesDetails();
        details.setSeasons(List.of(season));

        Content c = new Content();
        c.setId(TEST_ID);
        c.setType(ContentType.SERIES);
        c.setStatus(ContentStatus.DRAFT);
        c.setTitle("Serie de prueba");
        c.setSynopsis("Documento creado por MongoMappingIT.");
        c.setReleaseYear(2026);
        c.setGenres(List.of("Drama"));
        c.setProductionCountries(List.of("Colombia"));
        c.setAudioLanguages(List.of("es"));
        c.setSubtitleLanguages(List.of());
        c.setAgeRating(12);
        c.setCoverUrl("https://img.cineconecta.test/SER-99999.jpg");
        c.setSeries(details);
        return c;
    }
}
