package com.cineconecta.backend.dto.playback;

import jakarta.validation.constraints.NotBlank;

/** episodeId is required for a SERIES and forbidden otherwise (RF45, RF49); checked in the service. */
public record PlaybackStartRequest(@NotBlank String contentId, String episodeId) {
}
