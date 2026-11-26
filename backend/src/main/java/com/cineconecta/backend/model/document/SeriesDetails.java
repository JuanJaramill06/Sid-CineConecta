package com.cineconecta.backend.model.document;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded in Content when type = SERIES. */
@Getter
@Setter
@NoArgsConstructor
public class SeriesDetails {
    private List<Season> seasons;
}
