package com.cineconecta.backend.dto.catalog;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Required when ContentRequest.type() = DOCUMENTARY (RF31). sources is optional. */
public record DocumentaryRequest(@NotNull @Positive Integer durationSeconds,
                                  @NotEmpty List<String> directors, @NotEmpty List<String> topics,
                                  @NotEmpty List<String> interviewees, List<String> sources) {
}
