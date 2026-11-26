package com.cineconecta.backend.dto.catalog;

/** character is null when it is not known (RF27). */
public record CastMemberResponse(String actor, String character) {
}
