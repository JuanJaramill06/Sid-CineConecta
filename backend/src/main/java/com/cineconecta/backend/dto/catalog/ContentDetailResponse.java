package com.cineconecta.backend.dto.catalog;

import java.util.List;

import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;

/** Full content (RF25-RF31). Exactly one of movie/series/documentary is set, matching type. */
public record ContentDetailResponse(String id, ContentType type, ContentStatus status, String title,
                                     String synopsis, Integer releaseYear, List<String> genres,
                                     List<String> productionCountries, List<String> audioLanguages,
                                     List<String> subtitleLanguages, Integer ageRating, String coverUrl,
                                     MovieDetailsResponse movie, SeriesDetailsResponse series,
                                     DocumentaryDetailsResponse documentary) {
}
