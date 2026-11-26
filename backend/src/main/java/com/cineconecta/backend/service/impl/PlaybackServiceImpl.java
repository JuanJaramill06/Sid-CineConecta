package com.cineconecta.backend.service.impl;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
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

/**
 * Every transition is ONE conditional pipeline update on Watch_sessions, exactly like
 * database/mongodb/06_operations.js section 5:
 *   - the filter matches _id AND the allowed source states, so a second concurrent request for
 *     the same transition matches nothing (no duplicated watchedSeconds);
 *   - the time played is computed by MongoDB from the document's CURRENT playingSince and
 *     $$NOW at write time, never from a value read earlier in Java, so two different
 *     transitions racing (pause vs seek vs heartbeat) cannot overwrite each other's increment.
 * The session is re-read afterwards and returned as it really is.
 */
@Service
public class PlaybackServiceImpl implements PlaybackService {

    private static final String COLLECTION = "Watch_sessions";
    private static final String PLAYING = PlaybackState.PLAYING.name();
    private static final String PAUSED = PlaybackState.PAUSED.name();
    private static final String ENDED = PlaybackState.ENDED.name();

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

        // Close any session this profile left open for the same content (tab closed, crash).
        // It ends at its last saved activity: the heartbeat already stored the time played and
        // the position, and nothing after that moment is counted (no inflated reports).
        mongoTemplate.getCollection(COLLECTION).updateMany(
                new Document("profileId", profile.getProfileId())
                        .append("contentId", content.getId())
                        .append("state", new Document("$ne", ENDED)),
                List.of(new Document("$set", new Document("state", ENDED).append("endedAt", "$lastActivityAt")),
                        new Document("$unset", "playingSince")));

        Instant now = Instant.now();
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

    // PLAYING -> PAUSED, adding what was played since playingSince.
    @Override
    public PlaybackStateResponse pause(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() == PlaybackState.PLAYING) {
            update(sessionId, List.of(PLAYING), List.of(
                    new Document("$set", new Document("watchedSeconds", plus("$watchedSeconds", elapsed()))
                            .append("lastPositionSeconds", plus("$lastPositionSeconds", elapsed()))
                            .append("state", PAUSED)
                            .append("lastActivityAt", "$$NOW")),
                    completedStage(),
                    new Document("$unset", "playingSince")));
        }
        return reread(profileId, sessionId);
    }

    // PAUSED -> PLAYING.
    @Override
    public PlaybackStateResponse resume(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() == PlaybackState.ENDED) {
            throw new BusinessRuleException("La sesión ya finalizó");
        }
        if (session.getState() == PlaybackState.PAUSED) {
            update(sessionId, List.of(PAUSED), List.of(
                    new Document("$set", new Document("state", PLAYING)
                            .append("playingSince", "$$NOW")
                            .append("lastActivityAt", "$$NOW"))));
        }
        return reread(profileId, sessionId);
    }

    // RF47, RF53: from PLAYING, the time up to the jump is added and the clock restarts at the
    // new position; from PAUSED, only the position moves. The jump itself never counts.
    @Override
    public PlaybackStateResponse seek(Integer profileId, String sessionId, PlaybackEventRequest request) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() == PlaybackState.ENDED) {
            throw new BusinessRuleException("La sesión ya finalizó");
        }
        int position = clampPosition(request.positionSeconds(), session.getDurationSeconds());
        update(sessionId, List.of(PLAYING, PAUSED), List.of(
                new Document("$set", new Document("watchedSeconds",
                        plus("$watchedSeconds", whenPlaying(elapsed(), 0)))
                        .append("lastPositionSeconds", position)
                        .append("playingSince", whenPlaying("$$NOW", "$$REMOVE"))
                        .append("lastActivityAt", "$$NOW")),
                completedStage()));
        return reread(profileId, sessionId);
    }

    // Periodic save while PLAYING: same increment as pause, but the session keeps playing.
    @Override
    public PlaybackStateResponse heartbeat(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() == PlaybackState.PLAYING) {
            update(sessionId, List.of(PLAYING), List.of(
                    new Document("$set", new Document("watchedSeconds", plus("$watchedSeconds", elapsed()))
                            .append("lastPositionSeconds", plus("$lastPositionSeconds", elapsed()))
                            .append("playingSince", "$$NOW")
                            .append("lastActivityAt", "$$NOW")),
                    completedStage()));
        }
        return reread(profileId, sessionId);
    }

    // RF48, RF56: PLAYING/PAUSED -> ENDED, completed. From PLAYING the last stretch is added.
    @Override
    public PlaybackStateResponse finish(Integer profileId, String sessionId) {
        WatchSession session = ownedSession(profileId, sessionId);
        if (session.getState() != PlaybackState.ENDED) {
            update(sessionId, List.of(PLAYING, PAUSED), List.of(
                    new Document("$set", new Document("watchedSeconds",
                            plus("$watchedSeconds", whenPlaying(elapsed(), 0)))
                            .append("lastPositionSeconds", plus("$lastPositionSeconds", whenPlaying(elapsed(), 0)))
                            .append("state", ENDED)
                            .append("endedAt", "$$NOW")
                            .append("lastActivityAt", "$$NOW")
                            .append("completed", true)),
                    new Document("$unset", "playingSince")));
        }
        return reread(profileId, sessionId);
    }

    // ---- Pipeline pieces (same expressions as 06_operations.js) -----------------------------

    private void update(String sessionId, List<String> fromStates, List<Document> pipeline) {
        mongoTemplate.getCollection(COLLECTION).updateOne(
                new Document("_id", new ObjectId(sessionId))
                        .append("state", new Document("$in", fromStates)),
                pipeline);
    }

    /**
     * Seconds played since playingSince, by the database clock, capped at what the content
     * still had left (durationSeconds - lastPositionSeconds): a session forgotten in PLAYING
     * never counts more than the rest of the content.
     */
    private static Document elapsed() {
        return new Document("$max", List.of(0, new Document("$min", List.of(
                new Document("$dateDiff", new Document("startDate", "$playingSince")
                        .append("endDate", "$$NOW").append("unit", "second")),
                new Document("$subtract", List.of("$durationSeconds", "$lastPositionSeconds"))))));
    }

    // $dateDiff returns a long, so the sum would be stored as int64; $toInt keeps the fields
    // int32 like every other session (the values are seconds, far below 2^31).
    private static Document plus(String field, Object amount) {
        return new Document("$toInt", new Document("$add", List.of(field, amount)));
    }

    private static Document whenPlaying(Object ifPlaying, Object otherwise) {
        return new Document("$cond", List.of(new Document("$eq", List.of("$state", PLAYING)), ifPlaying, otherwise));
    }

    // RF56: once the position reaches 90 % of the duration the session stays completed,
    // even if the user later seeks back. Runs after the $set that moved the position.
    private static Document completedStage() {
        return new Document("$set", new Document("completed", new Document("$or", List.of("$completed",
                new Document("$gte", List.of("$lastPositionSeconds",
                        new Document("$multiply", List.of(0.9, "$durationSeconds"))))))));
    }

    // ---- Rules -----------------------------------------------------------------------------

    private WatchSession ownedSession(Integer profileId, String sessionId) {
        if (!ObjectId.isValid(sessionId)) {
            throw new ForbiddenOperationException("La sesión no pertenece al perfil");
        }
        return sessions.findByIdAndProfileId(sessionId, profileId)
                .orElseThrow(() -> new ForbiddenOperationException("La sesión no pertenece al perfil"));
    }

    private PlaybackStateResponse reread(Integer profileId, String sessionId) {
        return mapper.toState(ownedSession(profileId, sessionId));
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

    private static int clampPosition(int requested, int durationSeconds) {
        return Math.max(0, Math.min(requested, durationSeconds));
    }
}
