package com.cineconecta.backend.dto.playback;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Used for "seek" (RF47): the new position, clamped to the content's duration. */
public record PlaybackEventRequest(@NotNull @PositiveOrZero Integer positionSeconds) {
}
