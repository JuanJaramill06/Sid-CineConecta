package com.cineconecta.backend.model.document;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded in SeriesDetails. seasonNumber is unique within the series (validated by the app). */
@Getter
@Setter
@NoArgsConstructor
public class Season {
    private Integer seasonNumber;
    private String description;
    private List<Episode> episodes;
}
