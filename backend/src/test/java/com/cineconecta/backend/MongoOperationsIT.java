package com.cineconecta.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

import com.cineconecta.backend.config.MongoConfig;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.dto.playback.PlaybackEventRequest;
import com.cineconecta.backend.dto.playback.PlaybackStartRequest;
import com.cineconecta.backend.dto.playback.PlaybackStateResponse;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.mapper.ContentMapper;
import com.cineconecta.backend.mapper.WatchSessionMapper;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.PlaybackState;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.mongo.ContentIdGenerator;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.repository.mongo.MyListRepository;
import com.cineconecta.backend.repository.mongo.WatchSessionRepository;
import com.cineconecta.backend.repository.relational.PaymentRepository;
import com.cineconecta.backend.repository.relational.PlanRepository;
import com.cineconecta.backend.service.PlaybackAuthorization;
import com.cineconecta.backend.service.PlaybackEligibilityService;
import com.cineconecta.backend.service.impl.ContentAdminServiceImpl;
import com.cineconecta.backend.service.impl.MyListServiceImpl;
import com.cineconecta.backend.service.impl.PlaybackServiceImpl;
import com.cineconecta.backend.service.impl.ReportServiceImpl;

/**
 * The MongoDB side of the services against a REAL database loaded with 01_collections.js,
 * 02_indexes.js and 03_seed.js: the pipeline updates must pass the $jsonSchema validators and
 * compute the time with $$NOW, and the report must use its covered plan. Oracle is mocked.
 *
 * Writes only sessions and My list items of profile 999 (not in any seed) and removes them.
 * Run, for example against a local container:
 *   $env:MONGO_URI = 'mongodb://localhost:27099'; .\mvnw.cmd test -Dtest=MongoOperationsIT
 */
@DataMongoTest
@Import(MongoConfig.class)
@EnabledIfEnvironmentVariable(named = "MONGO_URI", matches = ".+")
class MongoOperationsIT {

    private static final int PROFILE = 999;
    private static final String MOVIE = "MOV-00001"; // PUBLISHED, rating 12, 6600 s in the seed

    @Autowired MongoTemplate mongoTemplate;
    @Autowired WatchSessionRepository sessions;
    @Autowired ContentRepository contents;
    @Autowired MyListRepository myList;

    private final PlaybackEligibilityService eligibility = mock(PlaybackEligibilityService.class);
    private final Profile profile = mock(Profile.class);
    private PlaybackServiceImpl playback;

    @BeforeEach
    void setUp() {
        cleanUp();
        when(profile.getProfileId()).thenReturn(PROFILE);
        when(profile.getMaxAgeRating()).thenReturn(12);
        Content movie = contents.findById(MOVIE).orElseThrow(() -> new IllegalStateException("load 03_seed.js first"));
        // Oracle part mocked: current subscription 1, plan 3 (Premium) as the snapshot
        when(eligibility.authorize(any(), eq(profile), eq(MOVIE))).thenReturn(new PlaybackAuthorization(movie, 1, 3));
        playback = new PlaybackServiceImpl(sessions, mongoTemplate, eligibility, Mappers.getMapper(WatchSessionMapper.class));
    }

    @AfterEach
    void cleanUp() {
        mongoTemplate.getCollection("Watch_sessions").deleteMany(new Document("profileId", PROFILE));
        mongoTemplate.getCollection("MyList").deleteMany(new Document("profileId", PROFILE));
    }

    /** Pretends the session has been playing for the given seconds. */
    private void playedFor(String sessionId, int seconds) {
        mongoTemplate.getCollection("Watch_sessions").updateOne(new Document("_id", new ObjectId(sessionId)),
                new Document("$set", new Document("playingSince", Date.from(Instant.now().minusSeconds(seconds)))));
    }

    private Document raw(String sessionId) {
        return mongoTemplate.getCollection("Watch_sessions").find(new Document("_id", new ObjectId(sessionId))).first();
    }

    private PlaybackStateResponse start() {
        return playback.start(1, profile, new PlaybackStartRequest(MOVIE, null));
    }

    @Test
    void fullLifecyclePassesTheValidatorsAndNeverCountsJumps() {
        String id = start().sessionId();

        playedFor(id, 60);
        PlaybackStateResponse s = playback.heartbeat(PROFILE, id);
        assertThat(s.state()).isEqualTo(PlaybackState.PLAYING);
        assertThat(s.watchedSeconds()).isBetween(60, 62);
        assertThat(s.lastPositionSeconds()).isBetween(60, 62);

        playedFor(id, 30);
        s = playback.seek(PROFILE, id, new PlaybackEventRequest(3000));
        assertThat(s.watchedSeconds()).isBetween(90, 93);         // the 30 s before the jump count
        assertThat(s.lastPositionSeconds()).isEqualTo(3000);      // the jump itself does not (RF53)
        assertThat(raw(id).get("playingSince")).isNotNull();

        playedFor(id, 10);
        s = playback.pause(PROFILE, id);
        assertThat(s.state()).isEqualTo(PlaybackState.PAUSED);
        assertThat(s.watchedSeconds()).isBetween(100, 104);
        assertThat(raw(id)).doesNotContainKey("playingSince");

        s = playback.seek(PROFILE, id, new PlaybackEventRequest(6000)); // >= 90 % of 6600, while PAUSED
        assertThat(s.watchedSeconds()).isBetween(100, 104);       // paused: no time added
        assertThat(s.completed()).isTrue();
        assertThat(raw(id)).doesNotContainKey("playingSince");    // $$REMOVE worked

        s = playback.seek(PROFILE, id, new PlaybackEventRequest(100));
        assertThat(s.completed()).isTrue();                       // stays completed (RF56)

        playback.resume(PROFILE, id);
        playedFor(id, 20);
        s = playback.finish(PROFILE, id);
        assertThat(s.state()).isEqualTo(PlaybackState.ENDED);
        assertThat(s.watchedSeconds()).isBetween(120, 126);
        assertThat(raw(id).get("endedAt")).isNotNull();
    }

    @Test
    void positionAndTimeNeverPassTheDuration() {
        String id = start().sessionId();

        PlaybackStateResponse s = playback.seek(PROFILE, id, new PlaybackEventRequest(99_999));
        assertThat(s.lastPositionSeconds()).isEqualTo(6600);      // clamped to the duration

        playback.seek(PROFILE, id, new PlaybackEventRequest(6000));
        playedFor(id, 100_000); // forgotten tab: far longer than the movie
        s = playback.pause(PROFILE, id);

        assertThat(s.lastPositionSeconds()).isEqualTo(6600);      // only the 600 s that were left
        assertThat(s.watchedSeconds()).isBetween(600, 602);
    }

    @Test
    void continueWatchingFromTheSeed() {
        var service = new com.cineconecta.backend.service.impl.ContinueWatchingServiceImpl(mongoTemplate);

        // Profile 4: SER-00002, last episode completed -> next episode from 0 (06_operations.js op 6)
        var profile4 = service.list(4);
        assertThat(profile4).anySatisfy(item -> {
            assertThat(item.contentId()).isEqualTo("SER-00002");
            assertThat(item.episodeId()).isEqualTo("SER-00002-S02E01");
            assertThat(item.positionSeconds()).isZero();
        });

        // Profile 1: withdrawn MOV-00006 and completed MOV-00002 are not offered (RF61)
        assertThat(service.list(1)).extracting(item -> item.contentId()).doesNotContain("MOV-00006", "MOV-00002");

        // Profile 3 finished its series: nothing to continue
        assertThat(service.list(3)).isEmpty();
    }

    @Test
    void twoSimultaneousPausesAddTheTimeOnce() {
        String id = start().sessionId();
        playedFor(id, 50);

        CompletableFuture<PlaybackStateResponse> a = CompletableFuture.supplyAsync(() -> playback.pause(PROFILE, id));
        CompletableFuture<PlaybackStateResponse> b = CompletableFuture.supplyAsync(() -> playback.pause(PROFILE, id));
        CompletableFuture.allOf(a, b).join();

        assertThat(raw(id).get("watchedSeconds")).isInstanceOf(Integer.class); // stored as int32
        assertThat(raw(id).getInteger("watchedSeconds")).isBetween(50, 52);
    }

    @Test
    void startingAgainClosesTheAbandonedSessionAtItsLastHeartbeat() {
        String abandoned = start().sessionId();
        playedFor(abandoned, 40);
        playback.heartbeat(PROFILE, abandoned);
        Date lastHeartbeat = raw(abandoned).getDate("lastActivityAt");

        String fresh = start().sessionId(); // the tab was closed; the profile opens the movie again

        Document old = raw(abandoned);
        assertThat(old.getString("state")).isEqualTo("ENDED");
        assertThat(old.getDate("endedAt")).isEqualTo(lastHeartbeat);
        assertThat(old.getInteger("watchedSeconds")).isBetween(40, 42);
        assertThat(raw(fresh).getString("state")).isEqualTo("PLAYING");
    }

    @Test
    void myListOnlyAcceptsPublishedContentAllowedForTheProfile() {
        MyListServiceImpl service = new MyListServiceImpl(myList, contents);

        assertThatThrownBy(() -> service.add(profile, "MOV-00007")).isInstanceOf(ForbiddenOperationException.class); // DRAFT
        assertThatThrownBy(() -> service.add(profile, "MOV-00006")).isInstanceOf(ForbiddenOperationException.class); // WITHDRAWN
        assertThatThrownBy(() -> service.add(profile, "MOV-00002")).isInstanceOf(ForbiddenOperationException.class); // rating 18
        assertThat(service.add(profile, MOVIE).available()).isTrue();
    }

    @Test
    void adminListIsPaginatedAndReadsNoSubdocuments() {
        ContentAdminServiceImpl admin = new ContentAdminServiceImpl(contents, new ContentIdGenerator(mongoTemplate),
                Mappers.getMapper(ContentMapper.class));

        var firstPage = admin.list(null, null, 0, 5);
        assertThat(firstPage.items()).hasSize(5);
        assertThat(firstPage.totalItems()).isGreaterThanOrEqualTo(20);

        var drafts = admin.list(ContentStatus.DRAFT, null, 0, 100);
        assertThat(drafts.items()).extracting(ContentSummaryResponse::id).contains("MOV-00007", "SER-00006");
        assertThat(drafts.items()).allMatch(c -> c.status() == ContentStatus.DRAFT);

        assertThat(contents.findSummaries(PageRequest.of(0, 100)).getContent())
                .allMatch(c -> c.getSeries() == null && c.getMovie() == null && c.getDocumentary() == null);
    }

    @Test
    void consumptionReportRunsAndItsQueryIsCovered() {
        MongoTemplate watched = spy(mongoTemplate);
        PlanRepository plans = mock(PlanRepository.class);
        when(plans.findAll()).thenReturn(List.of());
        ReportServiceImpl reports = new ReportServiceImpl(mock(PaymentRepository.class), plans, watched);

        var rows = reports.consumptionByPlanAndType(LocalDate.now().minusDays(90), LocalDate.now());
        assertThat(rows).isNotEmpty(); // the seed has sessions under several plans

        ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(watched).aggregate(aggregation.capture(), eq("Watch_sessions"), eq(Document.class));
        List<Document> pipeline = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        Document explain = mongoTemplate.getDb().runCommand(new Document("explain",
                new Document("aggregate", "Watch_sessions").append("pipeline", pipeline).append("cursor", new Document()))
                .append("verbosity", "queryPlanner"));
        String plan = explain.toJson();
        assertThat(plan).contains("ix_sessions_report").doesNotContain("COLLSCAN").doesNotContain("\"FETCH\"");
    }
}
