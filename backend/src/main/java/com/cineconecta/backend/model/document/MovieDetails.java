package com.cineconecta.backend.model.document;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded in Content when type = MOVIE. */
@Getter
@Setter
@NoArgsConstructor
public class MovieDetails {
    private Integer durationSeconds;
    private List<String> directors;
    private List<CastMember> cast;
}
