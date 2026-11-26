package com.cineconecta.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cineconecta.backend.dto.playback.PlaybackEventRequest;
import com.cineconecta.backend.dto.playback.PlaybackStartRequest;
import com.cineconecta.backend.dto.playback.PlaybackStateResponse;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.exception.ResourceNotFoundException;
import com.cineconecta.backend.mapper.WatchSessionMapper;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.document.Episode;
import com.cineconecta.backend.model.document.Season;
import com.cineconecta.backend.model.document.WatchSession;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.enums.PlaybackState;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.mongo.WatchSessionRepository;
import com.cineconecta.backend.service.PlaybackAuthorization;
import com.cineconecta.backend.service.PlaybackEligibilityService;
import com.cineconecta.backend.service.PlaybackService;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Every transition is a MongoTemplate conditional update (match on _id AND state), mirroring
 * database/mongodb/06_operations.js section 5: a second, concurrent request for the same
 * transition simply does not match anything, and the re-read afterwards returns whatever the
 * first one left — never a duplicated watchedSeconds.
 */
@Service
public class PlaybackServiceImpl implements PlaybackService {

    private final WatchSessionRepository sessions;
    private final MongoTemplate mongoTemplate;
    private final PlaybackEligibilityService eligibility;
    private final WatchSessionMapper mapper;

    public PlaybackServiceImpl(WatchSessionRepository sessions, MongoTemplate mongoTemplate,
                              PlaybackEligibilityService eligibility, WatchSessionMapper mapper) {
        this.sessions = sessions;
        this.mongoTemplate = mongoTemplate;
        this.eligibility = eligibility;
        this.mapper = mapper;
    }

    @Override
    public PlaybackStateResponse start(Integer accountId, Profile profile, PlaybackStartRequest request) {
        PlaybackAuthorization auth = eligibility.authorize(accountId, profile, request.contentId());
        Content content = auth.content();
        requireEpisodeMatchesType(content, request.episodeId());
        int durationSeconds = resolveDurationSeconds(content, request.episodeId());

        // Close any session this profile left open for the same content (abandoned).
        Instant now = Instant.now();
        mongoTemplate.updateMulti(
                Query.query(where("profileId").is(profile.getProfileId())
                        .and("contentId").is(content.getId())
                        .and("state").ne(PlaybackState.ENDED)),
                new Update().set("state", PlaybackState.ENDED).set("endedAt", now)
                        .set("lastActivityAt", now).unset("playingSince"),
                WatchSession.class);

        WatchSession session = new WatchSession();
        session.setProfileId(profile.getProfileId());
        session.setSubscriptionId(auth.subscriptionId());
        session.setPlanId(auth.planId());
        session.setContentId(content.getId());
        session.setContentType(content.getType());
        session.setEpisodeId(request.episodeId());
        session.setStartedAt(now);
        session.setWatchedSeconds(0);
        session.setLastPositionSeconds(0);
        session.setDurationSeconds(durationSeconds);
        session.setCompleted(false);
        session.setState(PlaybackState.PLAYING);
        session.setPlayingSince(now);
        session.setLastActivityAt(now);
        return mapper.toState(sessions.save(session));
    }

    @Override
    public PlaybackStateResponse pause(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() == PlaybackState.PLAYING) {
            int elapsed = elapsedSince(session);
            int watched = session.getWatchedSeconds() + elapsed;
            int position = session.getLastPositionSeconds() + elapsed;
            Instant now = Instant.now();
            mongoTemplate.updateFirst(
                    Query.query(where("_id").is(sessionId).and("state").is(PlaybackState.PLAYING)),
                    new Update().set("watchedSeconds", watched).set("lastPositionSeconds", position)
                            .set("completed", isCompleted(position, session.getDurationSeconds()))
                            .set("state", PlaybackState.PAUSED).set("lastActivityAt", now).unset("playingSince"),
                    WatchSession.class);
        }
        return mapper.toState(ownedSession(profileId, sessionId));
    }

    @Override
    public PlaybackStateResponse resume(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() == PlaybackState.ENDED) {
            throw new BusinessRuleException("La sesión ya finalizó");
        }
        if (session.getState() == PlaybackState.PAUSED) {
            Instant now = Instant.now();
            mongoTemplate.updateFirst(
                    Query.query(where("_id").is(sessionId).and("state").is(PlaybackState.PAUSED)),
                    new Update().set("state", PlaybackState.PLAYING).set("playingSince", now)
                            .set("lastActivityAt", now),
                    WatchSession.class);
        }
        return mapper.toState(ownedSession(profileId, sessionId));
    }

    @Override
    public PlaybackStateResponse seek(Integer profileId, String sessionId, PlaybackEventRequest request) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() != PlaybackState.PLAYING) {
            throw new BusinessRuleException("Solo se puede avanzar mientras se está reproduciendo");
        }
        int elapsed = elapsedSince(session);
        int watched = session.getWatchedSeconds() + elapsed;
        int position = clampPosition(request.positionSeconds(), session.getDurationSeconds());
        Instant now = Instant.now();
        mongoTemplate.updateFirst(
                Query.query(where("_id").is(sessionId).and("state").is(PlaybackState.PLAYING)),
                new Update().set("watchedSeconds", watched).set("lastPositionSeconds", position)
                        .set("completed", isCompleted(position, session.getDurationSeconds()))
                        .set("playingSince", now).set("lastActivityAt", now),
                WatchSession.class);
        return mapper.toState(ownedSession(profileId, sessionId));
    }

    @Override
    public PlaybackStateResponse finish(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() != PlaybackState.ENDED) {
            int elapsed = session.getState() == PlaybackState.PLAYING ? elapsedSince(session) : 0;
            int watched = session.getWatchedSeconds() + elapsed;
            Instant now = Instant.now();
            mongoTemplate.updateFirst(
                    Query.query(where("_id").is(sessionId).and("state").in(PlaybackState.PLAYING, PlaybackState.PAUSED)),
                    new Update().set("watchedSeconds", watched).set("state", PlaybackState.ENDED)
                            .set("completed", true).set("endedAt", now).set("lastActivityAt", now)
                            .unset("playingSince"),
                    WatchSession.class);
        }
        return mapper.toState(ownedSession(profileId, sessionId));
    }

    // ---- Rules -----------------------------------------------------------------------------

    private WatchSession ownedSession(Integer profileId, String sessionId) {
        return sessions.findByIdAndProfileId(sessionId, profileId)
                .orElseThrow(() -> new ForbiddenOperationException("La sesión no pertenece al perfil"));
    }

    // RF49: series play by episode; a movie/documentary never takes one.
    private void requireEpisodeMatchesType(Content content, String episodeId) {
        if (content.getType() == ContentType.SERIES && episodeId == null) {
            throw new BusinessRuleException("Las series se reproducen por episodio");
        }
        if (content.getType() != ContentType.SERIES && episodeId != null) {
            throw new BusinessRuleException("Solo las series admiten episodio");
        }
    }

    private int resolveDurationSeconds(Content content, String episodeId) {
        return switch (content.getType()) {
            case MOVIE -> content.getMovie().getDurationSeconds();
            case DOCUMENTARY -> content.getDocumentary().getDurationSeconds();
            case SERIES -> findEpisode(content, episodeId).getDurationSeconds();
        };
    }

    private Episode findEpisode(Content content, String episodeId) {
        return content.getSeries().getSeasons().stream()
                .map(Season::getEpisodes).flatMap(List<Episode>::stream)
                .filter(episode -> episode.getId().equals(episodeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("El episodio no existe: " + episodeId));
    }

    // Server clock, capped so a session left PLAYING for hours never counts more than the
    // content still had left (durationSeconds - lastPositionSeconds).
    private int elapsedSince(WatchSession session) {
        long raw = Duration.between(session.getPlayingSince(), Instant.now()).getSeconds();
        long cap = session.getDurationSeconds() - session.getLastPositionSeconds();
        return (int) Math.max(0, Math.min(raw, cap));
    }

    private int clampPosition(int requested, int durationSeconds) {
        return Math.max(0, Math.min(requested, durationSeconds));
    }

    // RF56: finished or at/above 90% of the duration.
    private boolean isCompleted(int positionSeconds, int durationSeconds) {
        return positionSeconds >= durationSeconds * 0.9;
    }
}
