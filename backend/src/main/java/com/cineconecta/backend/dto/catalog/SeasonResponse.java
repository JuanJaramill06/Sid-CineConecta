package com.cineconecta.backend.dto.catalog;

import java.util.List;

public record SeasonResponse(Integer seasonNumber, String description, List<EpisodeResponse> episodes) {
}
