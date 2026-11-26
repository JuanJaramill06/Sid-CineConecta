package com.cineconecta.backend.model.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.enums.PlaybackState;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One simulated playback session. Closed sessions are never modified (history). */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "Watch_sessions")
public class WatchSession {

    @Id
    private String id;

    private Integer profileId;
    // Snapshot taken when the session starts: the account's plan may change later (RF68)
    private Integer subscriptionId;
    private Integer planId;

    private String contentId;
    private ContentType contentType;
    private String episodeId; // only for SERIES

    private Instant startedAt;
    private Instant endedAt;
    private Integer watchedSeconds; // excludes seek jumps (RF53)
    private Integer lastPositionSeconds;
    private Integer durationSeconds; // copy used by the 90 % completion rule
    private Boolean completed;
    private PlaybackState state;
    private Instant playingSince; // set while PLAYING
    private Instant lastActivityAt;
}
