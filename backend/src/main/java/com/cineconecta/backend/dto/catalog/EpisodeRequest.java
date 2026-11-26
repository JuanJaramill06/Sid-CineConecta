package com.cineconecta.backend.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * number must be unique within its season (RF29); the id is never sent by the client, it is
 * derived from the series id plus seasonNumber/number ("SER-00001-S01E01").
 */
public record EpisodeRequest(@NotNull @Positive Integer number, @NotBlank String title,
                              @NotBlank String synopsis, @NotNull @Positive Integer durationSeconds) {
}
