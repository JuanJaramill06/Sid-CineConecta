package com.cineconecta.backend.dto.catalog;

import java.util.List;

/** Opening a series (RF35) returns its seasons and episodes in this one response. */
public record SeriesDetailsResponse(List<SeasonResponse> seasons) {
}
