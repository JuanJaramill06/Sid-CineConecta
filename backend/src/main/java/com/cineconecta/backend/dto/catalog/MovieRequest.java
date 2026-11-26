package com.cineconecta.backend.dto.catalog;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Required when ContentRequest.type() = MOVIE (RF27). */
public record MovieRequest(@NotNull @Positive Integer durationSeconds,
                            @NotEmpty List<String> directors,
                            @Valid List<CastMemberRequest> cast) {
}
