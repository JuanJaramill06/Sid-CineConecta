package com.cineconecta.backend.model.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** counters collection: last sequence used per content id prefix ("MOV"|"SER"|"DOC"), RF63. */
@Getter
@NoArgsConstructor
@Document(collection = "counters")
public class Counter {

    @Id
    private String id;

    private long seq;
}
