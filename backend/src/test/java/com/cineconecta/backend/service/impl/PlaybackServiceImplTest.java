package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.cineconecta.backend.dto.playback.PlaybackEventRequest;
import com.cineconecta.backend.dto.playback.PlaybackStartRequest;
import com.cineconecta.backend.dto.playback.PlaybackStateResponse;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.mapper.WatchSessionMapper;
import com.cineconecta.backend.model.document.CastMember;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.document.Episode;
import com.cineconecta.backend.model.document.MovieDetails;
import com.cineconecta.backend.model.document.Season;
import com.cineconecta.backend.model.document.SeriesDetails;
import com.cineconecta.backend.model.document.WatchSession;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.enums.PlaybackState;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.mongo.WatchSessionRepository;
import com.cineconecta.backend.service.PlaybackAuthorization;
import com.cineconecta.backend.service.PlaybackEligibilityService;

import com.mongodb.client.MongoCollection;

/**
 * RF40-56: which conditional pipeline update reaches Mongo, and that a transition no longer
 * possible is a no-op, not an error. The pipelines themselves mirror 06_operations.js.
 */
class PlaybackServiceImplTest {

    private static final int ACCOUNT = 4;
    private static final int PROFILE = 4;
    private static final String SESSION = "507f1f77bcf86cd799439011"; // a valid ObjectId

    private final WatchSessionRepository sessions = mock(WatchSessionRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final PlaybackEligibilityService eligibility = mock(PlaybackEligibilityService.class);
    private final PlaybackServiceImpl service = new PlaybackServiceImpl(sessions, mongoTemplate, eligibility,
            Mappers.getMapper(WatchSessionMapper.class));

    private final Profile profile = mock(Profile.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> collection = mock(MongoCollection.class);

    @BeforeEach
    void nativeCollection() {
        when(mongoTemplate.getCollection("Watch_sessions")).thenReturn(collection);
    }

    private static Content movie(int durationSeconds) {
        Content content = new Content();
        content.setId("MOV-00001");
        content.setType(ContentType.MOVIE);
        content.setStatus(ContentStatus.PUBLISHED);
        MovieDetails details = new MovieDetails();
        details.setDurationSeconds(durationSeconds);
        details.setDirectors(List.of("Directora"));
        details.setCast(List.of(new CastMember()));
        content.setMovie(details);
        return content;
    }

    private static Content series() {
        Content content = new Content();
        content.setId("SER-00001");
        content.setType(ContentType.SERIES);
        content.setStatus(ContentStatus.PUBLISHED);
        Episode episode = new Episode();
        episode.setId("SER-00001-S01E01");
        episode.setNumber(1);
        episode.setDurationSeconds(1200);
        Season season = new Season();
        season.setSeasonNumber(1);
        season.setEpisodes(List.of(episode));
        SeriesDetails details = new SeriesDetails();
        details.setSeasons(List.of(season));
        content.setSeries(details);
        return content;
    }

    private static WatchSession session(PlaybackState state, int watched, int position, int duration) {
        WatchSession session = new WatchSession();
        session.setId(SESSION);
        session.setProfileId(PROFILE);
        session.setContentId("MOV-00001");
        session.setContentType(ContentType.MOVIE);
        session.setState(state);
        session.setWatchedSeconds(watched);
        session.setLastPositionSeconds(position);
        session.setDurationSeconds(duration);
        session.setCompleted(false);
        session.setPlayingSince(state == PlaybackState.PLAYING ? Instant.now().minusSeconds(30) : null);
        return session;
    }

    private void stored(WatchSession... versions) {
        if (versions.length == 1) {
            when(sessions.findByIdAndProfileId(SESSION, PROFILE)).thenReturn(Optional.of(versions[0]));
        } else {
            when(sessions.findByIdAndProfileId(SESSION, PROFILE)).thenReturn(Optional.of(versions[0]),
                    Optional.of(versions[1]));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Document> capturedPipeline() {
        ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(collection).updateOne(any(Bson.class), pipeline.capture());
        return pipeline.getValue();
    }

    private Document capturedFilter() {
        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(filter.capture(), anyList());
        return (Document) filter.getValue();
    }

    // ---- start ---------------------------------------------------------------------------------

    @Test
    void startRequiresAnEpisodeForASeries() {
        when(eligibility.authorize(ACCOUNT, profile, "SER-00001"))
                .thenReturn(new PlaybackAuthorization(series(), 1, 3));

        assertThatThrownBy(() -> service.start(ACCOUNT, profile, new PlaybackStartRequest("SER-00001", null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void startRejectsAnEpisodeForAMovie() {
        when(eligibility.authorize(ACCOUNT, profile, "MOV-00001"))
                .thenReturn(new PlaybackAuthorization(movie(3600), 1, 3));

        assertThatThrownBy(() -> service.start(ACCOUNT, profile,
                new PlaybackStartRequest("MOV-00001", "SER-00001-S01E01")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void startClosesAbandonedSessionsAtTheirLastActivityAndSavesTheOracleSnapshot() {
        when(profile.getProfileId()).thenReturn(PROFILE);
        when(eligibility.authorize(ACCOUNT, profile, "MOV-00001"))
                .thenReturn(new PlaybackAuthorization(movie(3600), 10, 3));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlaybackStateResponse response = service.start(ACCOUNT, profile, new PlaybackStartRequest("MOV-00001", null));

        assertThat(response.state()).isEqualTo(PlaybackState.PLAYING);
        assertThat(response.durationSeconds()).isEqualTo(3600);
        ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(collection).updateMany(any(Bson.class), pipeline.capture());
        Document set = pipeline.getValue().get(0).get("$set", Document.class);
        assertThat(set.get("endedAt")).isEqualTo("$lastActivityAt"); // nothing after the last heartbeat counts
    }

    // ---- pause / resume -------------------------------------------------------------------------

    @Test
    void pauseIsANoOpWhenAlreadyPaused() {
        stored(session(PlaybackState.PAUSED, 100, 100, 3600));

        PlaybackStateResponse response = service.pause(PROFILE, SESSION);

        assertThat(response.state()).isEqualTo(PlaybackState.PAUSED);
        verify(collection, never()).updateOne(any(Bson.class), anyList());
    }

    @Test
    void pauseOnlyMatchesAPlayingSessionAndComputesTimeInTheDatabase() {
        stored(session(PlaybackState.PLAYING, 0, 0, 3600), session(PlaybackState.PAUSED, 30, 30, 3600));

        PlaybackStateResponse response = service.pause(PROFILE, SESSION);

        assertThat(response.watchedSeconds()).isEqualTo(30);
        assertThat(capturedFilter().get("state", Document.class).getList("$in", String.class))
                .containsExactly("PLAYING"); // a second concurrent pause matches nothing
        assertThat(capturedPipeline().get(0).toJson()).contains("$$NOW", "$playingSince");
    }

    @Test
    void resumingAFinishedSessionIsRejected() {
        stored(session(PlaybackState.ENDED, 3600, 3600, 3600));

        assertThatThrownBy(() -> service.resume(PROFILE, SESSION)).isInstanceOf(BusinessRuleException.class);
    }

    // ---- seek -------------------------------------------------------------------------------------

    @Test
    void seekWorksWhilePausedAndClampsToTheDuration() {
        stored(session(PlaybackState.PAUSED, 100, 100, 3600), session(PlaybackState.PAUSED, 100, 3600, 3600));

        service.seek(PROFILE, SESSION, new PlaybackEventRequest(99_999));

        assertThat(capturedFilter().get("state", Document.class).getList("$in", String.class))
                .containsExactlyInAnyOrder("PLAYING", "PAUSED");
        Document set = capturedPipeline().get(0).get("$set", Document.class);
        assertThat(set.get("lastPositionSeconds")).isEqualTo(3600);
        // watched time only grows if it was PLAYING, by the time before the jump (RF53)
        assertThat(set.get("watchedSeconds", Document.class).toJson()).contains("$cond", "PLAYING");
    }

    @Test
    void seekOnAFinishedSessionIsRejected() {
        stored(session(PlaybackState.ENDED, 3600, 3600, 3600));

        assertThatThrownBy(() -> service.seek(PROFILE, SESSION, new PlaybackEventRequest(10)))
                .isInstanceOf(BusinessRuleException.class);
        verify(collection, never()).updateOne(any(Bson.class), anyList());
    }

    // ---- heartbeat ----------------------------------------------------------------------------------

    @Test
    void heartbeatSavesProgressAndKeepsPlaying() {
        stored(session(PlaybackState.PLAYING, 0, 0, 3600), session(PlaybackState.PLAYING, 30, 30, 3600));

        PlaybackStateResponse response = service.heartbeat(PROFILE, SESSION);

        assertThat(response.state()).isEqualTo(PlaybackState.PLAYING);
        Document set = capturedPipeline().get(0).get("$set", Document.class);
        assertThat(set).containsKeys("watchedSeconds", "lastPositionSeconds", "playingSince", "lastActivityAt");
        assertThat(set).doesNotContainKey("state");
    }

    @Test
    void heartbeatOnAPausedSessionIsANoOp() {
        stored(session(PlaybackState.PAUSED, 100, 100, 3600));

        service.heartbeat(PROFILE, SESSION);

        verify(collection, never()).updateOne(any(Bson.class), anyList());
    }

    // ---- finish and ownership -------------------------------------------------------------------------

    @Test
    void finishOnAnAlreadyEndedSessionIsANoOp() {
        stored(session(PlaybackState.ENDED, 3600, 3600, 3600));

        PlaybackStateResponse response = service.finish(PROFILE, SESSION);

        assertThat(response.state()).isEqualTo(PlaybackState.ENDED);
        verify(collection, never()).updateOne(any(Bson.class), anyList());
    }

    @Test
    void finishWhilePlayingUsesAnAtomicPipelineUpdate() {
        stored(session(PlaybackState.PLAYING, 0, 0, 3600), session(PlaybackState.ENDED, 10, 10, 3600));

        PlaybackStateResponse response = service.finish(PROFILE, SESSION);

        assertThat(response.state()).isEqualTo(PlaybackState.ENDED);
        assertThat(capturedPipeline().get(0).get("$set", Document.class).get("completed")).isEqualTo(true);
    }

    @Test
    void sessionOfAnotherProfileIsForbidden() {
        when(sessions.findByIdAndProfileId(SESSION, PROFILE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pause(PROFILE, SESSION)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.heartbeat(PROFILE, "not-an-object-id"))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
