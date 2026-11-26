package com.cineconecta.backend.dto.catalog;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** seasonNumber must be unique within the series (RF30). At least one episode (RF28). */
public record SeasonRequest(@NotNull @Positive Integer seasonNumber, @NotBlank String description,
                             @NotEmpty @Valid List<EpisodeRequest> episodes) {
}
