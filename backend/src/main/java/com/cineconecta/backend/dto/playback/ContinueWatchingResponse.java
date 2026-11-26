package com.cineconecta.backend.dto.playback;

import java.time.Instant;

import com.cineconecta.backend.model.enums.ContentType;

/**
 * RF57-61. For a series whose last episode was completed, episodeId/positionSeconds already
 * point at the next episode from 0; otherwise they resume the last session as-is.
 */
public record ContinueWatchingResponse(String contentId, String title, ContentType type, String coverUrl,
                                        Instant lastActivityAt, String episodeId, Integer positionSeconds) {
}
