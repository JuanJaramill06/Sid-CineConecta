package com.cineconecta.backend.dto.catalog;

import jakarta.validation.constraints.NotBlank;

/** character is optional: only set when it is known (RF27). */
public record CastMemberRequest(@NotBlank String actor, String character) {
}
