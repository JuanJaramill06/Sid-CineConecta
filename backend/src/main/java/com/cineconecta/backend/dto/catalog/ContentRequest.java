package com.cineconecta.backend.dto.catalog;

import java.util.List;

import com.cineconecta.backend.model.enums.ContentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Create or update a content (RF24-RF31). The id is never sent by the client: it is
 * generated from the "counters" collection on create and kept on update (RF63). Exactly one
 * of movie/series/documentary must match type(); ContentAdminServiceImpl rejects the rest.
 */
public record ContentRequest(@NotNull ContentType type, @NotBlank String title,
                              @NotBlank String synopsis, @NotNull Integer releaseYear,
                              @NotEmpty List<String> genres, @NotEmpty List<String> productionCountries,
                              @NotEmpty List<String> audioLanguages, List<String> subtitleLanguages,
                              @NotNull @Min(0) @Max(18) Integer ageRating, @NotBlank String coverUrl,
                              @Valid MovieRequest movie, @Valid SeriesRequest series,
                              @Valid DocumentaryRequest documentary) {
}
