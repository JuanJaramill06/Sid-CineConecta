package com.cineconecta.backend.service;

import com.cineconecta.backend.dto.playback.PlaybackEventRequest;
import com.cineconecta.backend.dto.playback.PlaybackStartRequest;
import com.cineconecta.backend.dto.playback.PlaybackStateResponse;
import com.cineconecta.backend.model.relational.Profile;

/** Start, pause, resume, seek and finish a simulated playback. */
public interface PlaybackService {

    /**
     * RF40-45, RF49. Authorizes with PlaybackEligibilityService, requires an episodeId for a
     * SERIES and rejects one otherwise, closes any session this profile left open for the same
     * content (abandoned), and opens a new one in PLAYING.
     */
    PlaybackStateResponse start(Integer accountId, Profile profile, PlaybackStartRequest request);

    /**
     * RF46: PLAYING -&gt; PAUSED, adding the time elapsed since playingSince (server clock).
     * A second, concurrent pause is a no-op: the conditional update only matches the first one.
     */
    PlaybackStateResponse pause(Integer profileId, String sessionId);

    /** PAUSED -&gt; PLAYING. */
    PlaybackStateResponse resume(Integer profileId, String sessionId);

    /**
     * RF47, RF53: moves lastPositionSeconds (clamped to the duration) while PLAYING or PAUSED.
     * Time played up to the jump is added; the jump itself never adds watched time.
     * An ENDED session: 422.
     */
    PlaybackStateResponse seek(Integer profileId, String sessionId, PlaybackEventRequest request);

    /**
     * RF52, RF54, RF57-59: while PLAYING, saves the time played and the position so far,
     * without changing the state. The client calls it every ~30 s, so closing the tab loses at
     * most that much and "Continue watching" keeps an up-to-date position. A no-op otherwise.
     */
    PlaybackStateResponse heartbeat(Integer profileId, String sessionId);

    /** RF48, RF56: PLAYING/PAUSED -&gt; ENDED, completed = true. */
    PlaybackStateResponse finish(Integer profileId, String sessionId);
}
