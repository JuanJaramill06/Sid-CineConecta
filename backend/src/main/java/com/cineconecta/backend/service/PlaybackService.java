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

    /** RF47, RF53: moves lastPositionSeconds; the jump itself never adds watched time. */
    PlaybackStateResponse seek(Integer profileId, String sessionId, PlaybackEventRequest request);

    /** RF48, RF56: PLAYING/PAUSED -&gt; ENDED, completed = true. */
    PlaybackStateResponse finish(Integer profileId, String sessionId);
}
