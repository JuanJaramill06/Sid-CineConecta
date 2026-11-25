package com.cineconecta.backend.dto.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// PROFILES.name VARCHAR2(60); maxAgeRating uses the same 0-18 scale as Content.ageRating.
public record ProfileRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull @Min(0) @Max(18) Integer maxAgeRating) {}
