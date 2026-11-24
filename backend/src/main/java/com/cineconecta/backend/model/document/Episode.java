package com.cineconecta.backend.model.document;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded in Season. number is unique within the season (validated by the app). */
@Getter
@Setter
@NoArgsConstructor
public class Episode {

    @Field("_id")
    private String id; // stable code: SER-00001-S01E01 (never reused)

    private Integer number;
    private String title;
    private String synopsis;
    private Integer durationSeconds;
}
