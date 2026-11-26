package com.cineconecta.backend.dto.catalog;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** Required when ContentRequest.type() = SERIES (RF28). At least one season. */
public record SeriesRequest(@NotEmpty @Valid List<SeasonRequest> seasons) {
}
