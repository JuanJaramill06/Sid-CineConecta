package com.cineconecta.backend.dto.playback;

import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.enums.PlaybackState;

public record PlaybackStateResponse(String sessionId, String contentId, String episodeId,
                                     ContentType contentType, PlaybackState state, Integer watchedSeconds,
                                     Integer lastPositionSeconds, Integer durationSeconds, Boolean completed) {
}
