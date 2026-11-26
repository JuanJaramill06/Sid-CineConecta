package com.cineconecta.backend.dto.catalog;

import java.util.List;

import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;

/** One row of a search result (RF34) or of the admin's content list. */
public record ContentSummaryResponse(String id, ContentType type, ContentStatus status, String title,
                                      Integer releaseYear, List<String> genres, Integer ageRating,
                                      String coverUrl) {
}
