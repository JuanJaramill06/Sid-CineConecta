package com.cineconecta.backend.model.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded in MovieDetails. character is null when unknown. */
@Getter
@Setter
@NoArgsConstructor
public class CastMember {
    private String actor;
    private String character;
}
