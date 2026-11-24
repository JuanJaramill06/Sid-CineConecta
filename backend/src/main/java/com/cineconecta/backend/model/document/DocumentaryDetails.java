package com.cineconecta.backend.model.document;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded in Content when type = DOCUMENTARY (single piece). sources is optional. */
@Getter
@Setter
@NoArgsConstructor
public class DocumentaryDetails {
    private Integer durationSeconds;
    private List<String> directors;
    private List<String> topics;
    private List<String> interviewees;
    private List<String> sources;
}
