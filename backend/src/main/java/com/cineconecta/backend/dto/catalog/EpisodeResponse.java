package com.cineconecta.backend.dto.catalog;

public record EpisodeResponse(String id, Integer number, String title, String synopsis,
                               Integer durationSeconds) {
}
