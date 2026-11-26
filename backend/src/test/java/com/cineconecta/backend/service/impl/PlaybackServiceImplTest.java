package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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

/** RF40-56: what reaches Mongo, and that a transition no longer possible is a no-op, not an error. */
class PlaybackServiceImplTest {

    private static final int ACCOUNT = 4;
    private static final int PROFILE = 4;

    private final WatchSessionRepository sessions = mock(WatchSessionRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final PlaybackEligibilityService eligibility = mock(PlaybackEligibilityService.class);
    private final PlaybackServiceImpl service = new PlaybackServiceImpl(sessions, mongoTemplate, eligibility,
            Mappers.getMapper(WatchSessionMapper.class));

    private final Profile profile = mock(Profile.class);

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

    private static WatchSession session(PlaybackState state, int watched, int position, int duration,
                                        Instant playingSince) {
        WatchSession session = new WatchSession();
        session.setId("session-1");
        session.setProfileId(PROFILE);
        session.setContentId("MOV-00001");
        session.setContentType(ContentType.MOVIE);
        session.setState(state);
        session.setWatchedSeconds(watched);
        session.setLastPositionSeconds(position);
        session.setDurationSeconds(duration);
        session.setCompleted(false);
        session.setPlayingSince(playingSince);
        return session;
    }

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
    void startSavesASessionWithTheOracleSnapshot() {
        when(profile.getProfileId()).thenReturn(PROFILE);
        when(eligibility.authorize(ACCOUNT, profile, "MOV-00001"))
                .thenReturn(new PlaybackAuthorization(movie(3600), 10, 3));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlaybackStateResponse response = service.start(ACCOUNT, profile, new PlaybackStartRequest("MOV-00001", null));

        assertThat(response.contentId()).isEqualTo("MOV-00001");
        assertThat(response.state()).isEqualTo(PlaybackState.PLAYING);
        assertThat(response.durationSeconds()).isEqualTo(3600);
        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(WatchSession.class));
    }

    @Test
    void pauseIsANoOpWhenAlreadyPaused() {
        WatchSession paused = session(PlaybackState.PAUSED, 100, 100, 3600, null);
        when(sessions.findByIdAndProfileId("session-1", PROFILE)).thenReturn(Optional.of(paused));

        PlaybackStateResponse response = service.pause(PROFILE, "session-1");

        assertThat(response.state()).isEqualTo(PlaybackState.PAUSED);
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(WatchSession.class));
    }

    @Test
    void pauseWhilePlayingUpdatesAndRereads() {
        WatchSession playing = session(PlaybackState.PLAYING, 0, 0, 3600, Instant.now().minusSeconds(30));
        WatchSession afterUpdate = session(PlaybackState.PAUSED, 30, 30, 3600, null);
        when(sessions.findByIdAndProfileId("session-1", PROFILE)).thenReturn(Optional.of(playing),
                Optional.of(afterUpdate));

        PlaybackStateResponse response = service.pause(PROFILE, "session-1");

        assertThat(response.state()).isEqualTo(PlaybackState.PAUSED);
        assertThat(response.watchedSeconds()).isEqualTo(30);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(WatchSession.class));
    }

    @Test
    void sessionOfAnotherProfileIsForbidden() {
        when(sessions.findByIdAndProfileId("session-1", PROFILE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pause(PROFILE, "session-1")).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void resumingAFinishedSessionIsRejected() {
        WatchSession ended = session(PlaybackState.ENDED, 3600, 3600, 3600, null);
        when(sessions.findByIdAndProfileId("session-1", PROFILE)).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> service.resume(PROFILE, "session-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void seekOnlyAllowedWhilePlaying() {
        WatchSession paused = session(PlaybackState.PAUSED, 100, 100, 3600, null);
        when(sessions.findByIdAndProfileId("session-1", PROFILE)).thenReturn(Optional.of(paused));

        assertThatThrownBy(() -> service.seek(PROFILE, "session-1", new PlaybackEventRequest(3000)))
                .isInstanceOf(BusinessRuleException.class);
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(WatchSession.class));
    }

    @Test
    void finishOnAnAlreadyEndedSessionIsANoOp() {
        WatchSession ended = session(PlaybackState.ENDED, 3600, 3600, 3600, null);
        when(sessions.findByIdAndProfileId("session-1", PROFILE)).thenReturn(Optional.of(ended));

        PlaybackStateResponse response = service.finish(PROFILE, "session-1");

        assertThat(response.state()).isEqualTo(PlaybackState.ENDED);
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(WatchSession.class));
    }
}
